package de.bornemisza.ds.users.da;

import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.core.MediaType;

import org.javalite.common.Util;
import org.javalite.http.Delete;
import org.javalite.http.Get;
import org.javalite.http.Http;
import org.javalite.http.HttpException;
import org.javalite.http.Put;
import org.javalite.http.Request;

import de.bornemisza.rest.HttpHeaders;
import de.bornemisza.rest.Json;
import de.bornemisza.rest.entity.Database;
import de.bornemisza.rest.entity.User;
import de.bornemisza.rest.entity.result.KeyValueViewResult;
import de.bornemisza.rest.entity.result.KeyValueViewResult.Row;
import de.bornemisza.rest.exception.BusinessException;
import de.bornemisza.rest.exception.DocumentNotFoundException;
import de.bornemisza.rest.exception.TechnicalException;
import de.bornemisza.rest.exception.UnauthorizedException;
import de.bornemisza.rest.exception.UpdateConflictException;
import de.bornemisza.rest.security.Auth;
import de.bornemisza.rest.security.Auth.Scheme;
import de.bornemisza.rest.security.BasicAuthCredentials;
import de.bornemisza.ds.users.boundary.UsersType;

public class UsersService {

    @Inject
    CouchUsersPoolAsAdmin usersPoolAsAdmin;

    @Inject
    CouchUsersPool usersPool;

    @Inject
    CouchPool couchPool;

    private static final String JSON_UTF8 = MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8").getType();
    private int maxWaitTimeForUserDatabaseInMillis = 10000;

    public UsersService() {
    }

    // Constructor for Unit Tests
    public UsersService(CouchUsersPoolAsAdmin pool1, CouchUsersPool pool2, CouchPool pool3, int timeout) {
        this.usersPoolAsAdmin = pool1;
        this.usersPool = pool2;
        this.couchPool = pool3;
        this.maxWaitTimeForUserDatabaseInMillis = timeout;
    }

    @PostConstruct
    public void init() throws BusinessException, TechnicalException {
        Http http = usersPool.getConnection().getHttp();
        Get get = http.get(http.getBaseUrl())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        try {
            int responseCode = get.responseCode();
            if (responseCode != 200) {
                throw new BusinessException(UsersType.UNEXPECTED, responseCode + ": " + get.responseMessage());
            }
            else {
                Database db = Json.fromJson(get.text(), Database.class);
                String msg = "DB: " + db.getDbName() + ", Documents: " + db.getDocCount() + ", Disk Size: " + db.getDiskSize();
                Logger.getLogger(http.getHostName()).info(msg);
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
    }

    public boolean existsUser(String userName) throws BusinessException, TechnicalException {
        User user = new User();
        user.setName(userName);
        Http http = usersPoolAsAdmin.getConnection().getHttp();
        Get get = http.get(http.getBaseUrl() + Util.urlEncode(user.getId()))
                .basic(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword()))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        try {
            int responseCode = get.responseCode();
            switch (responseCode) {
                case 404:
                    return false;
                case 200:
                    return true;
                default:
                    throw new BusinessException(UsersType.UNEXPECTED, responseCode + ": " + get.responseMessage());
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
    }

    public boolean existsEmail(InternetAddress email) throws BusinessException, TechnicalException {
        Http http = usersPoolAsAdmin.getConnection().getHttp();
        Get get = http.get(http.getBaseUrl() + "_design/User/_view/by_email")
                .basic(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword()))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        try {
            int responseCode = get.responseCode();
            if (responseCode != 200) {
                throw new BusinessException(UsersType.UNEXPECTED, responseCode + ": " + get.responseMessage());
            }
            else {
                String json = get.text();
                KeyValueViewResult viewResult = Json.fromJson(json, KeyValueViewResult.class);
                String emailStr = email.getAddress();
                for (Row row : viewResult.getRows()) {
                    if (emailStr.equals(row.getKey())) return true;
                }
                return false;
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
    }

    public User createUser(User user) throws BusinessException, TechnicalException, UpdateConflictException, DocumentNotFoundException, UnauthorizedException {
        Http http = usersPoolAsAdmin.getConnection().getHttp();
        Put put = http.put(http.getBaseUrl() + Util.urlEncode(user.getId()), Json.toJson(user))
                .basic(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword()))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_TYPE, JSON_UTF8);
        sendStatefulRequest(put, 201, 202);
        Auth auth = new Auth(new BasicAuthCredentials(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword())));
        User createdUser = readUser(auth, user.getId());
        Logger.getLogger(http.getHostName()).info("Added user: " + createdUser);
        String userDb = User.db(createdUser.getName());
        waitForDatabaseToBecomeAvailable(userDb);
        createStandardViews(userDb);
        return createdUser;
    }

    private void waitForDatabaseToBecomeAvailable(String userDb) throws TechnicalException {
        long start = System.currentTimeMillis();
        String lastError = "";
        while (System.currentTimeMillis() - start < maxWaitTimeForUserDatabaseInMillis) {
            Http http = couchPool.getConnection().getHttp();
            Get get = http.get(http.getBaseUrl() + userDb).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                    .basic(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword()));
            try {
                int responseCode = get.responseCode();
                if (responseCode == 200) {
                    long duration = System.currentTimeMillis() - start;
                    Logger.getLogger(http.getHostName()).info("Created database: " + userDb + " (Duration: " + duration + "ms)");
                    return;
                }
                else {
                    lastError = responseCode + ": " + get.text() + " " + http.getBaseUrl() + userDb;
                }
            }
            catch (HttpException ex) {
                Logger.getLogger(http.getHostName()).warning("While waiting for " + userDb + ": " + ex.toString());
            }
        }
        String msg = "User Database " + userDb + " was not created: " + lastError;
        Logger.getAnonymousLogger().severe(msg);
        throw new TechnicalException(msg);
    }

    private void createStandardViews(String userDb) {
        Http http = couchPool.getConnection().getHttp();
        Put put = http.put(http.getBaseUrl() + userDb + "/_design/Uuid", Views.getUuidSumByColor(userDb))
                .basic(usersPoolAsAdmin.getUserName(), String.valueOf(usersPoolAsAdmin.getPassword()))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_TYPE, JSON_UTF8);
        sendStatefulRequest(put, 201, 202);
        Logger.getLogger(http.getHostName()).info("Added view: uuid_sum_by_color");
    }

    public User updateUser(Auth auth, User user) throws BusinessException, TechnicalException, UnauthorizedException, UpdateConflictException, DocumentNotFoundException {
        Http http = usersPool.getConnection().getHttp();
        Put put = http.put(http.getBaseUrl() + Util.urlEncode(user.getId()), Json.toJson(user))
                .basic(auth.getUsername(), auth.getPassword())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_TYPE, JSON_UTF8);
        sendStatefulRequest(put, 201, 202);
        User updatedUser = readUser(auth, user.getId());
        Logger.getLogger(http.getHostName()).info("Updated user: " + updatedUser);
        return updatedUser;
    }

    public User getUser(Auth auth, String userName) throws BusinessException, DocumentNotFoundException, UnauthorizedException, TechnicalException {
        User user = new User();
        user.setName(userName);
        return readUser(auth, user.getId());
    }

    private User readUser(Auth auth, String userId) throws BusinessException, DocumentNotFoundException, TechnicalException, UnauthorizedException {
        Http http = usersPool.getConnection().getHttp();
        Get get = http.get(http.getBaseUrl() + Util.urlEncode(userId))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        if (auth.getScheme() == Scheme.COOKIE_CSRFTOKEN) {
            get = get.header(HttpHeaders.COOKIE, auth.getCookie());
        }
        else {
            get = get.basic(auth.getUsername(), auth.getPassword());
        }
        try {
            int responseCode = get.responseCode();
            if (responseCode == 401) {
                throw new UnauthorizedException(get.responseMessage());
            }
            else if (responseCode == 404) {
                throw new DocumentNotFoundException(get.responseMessage());
            }
            else if (responseCode != 200) {
                throw new BusinessException(UsersType.UNEXPECTED, responseCode + ": " + get.responseMessage());
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
        return Json.fromJson(get.text(), User.class);
    }

    public User changePassword(Auth auth, User user) throws BusinessException, TechnicalException, UnauthorizedException, UpdateConflictException, DocumentNotFoundException {
        Http http = usersPool.getConnection().getHttp();
        Put put = http.put(http.getBaseUrl() + Util.urlEncode(user.getId()), Json.toJson(user))
                .basic(auth.getUsername(), auth.getPassword())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_TYPE, JSON_UTF8);
        sendStatefulRequest(put, 201, 202);
        Logger.getLogger(http.getHostName()).info("Changed password for user: " + user);

        // change credentials to reflect new password
        auth.changePassword(String.valueOf(user.getPassword()));
        return readUser(auth, user.getId());
    }

    public void deleteUser(Auth auth, String userName, String rev) throws BusinessException, TechnicalException, UnauthorizedException, UpdateConflictException {
        User user = new User();
        user.setName(userName);
        Http http = usersPool.getConnection().getHttp();
        Delete delete = http.delete(http.getBaseUrl() + Util.urlEncode(user.getId()))
                .basic(auth.getUsername(), auth.getPassword())
                .header(HttpHeaders.IF_MATCH, rev);
        sendStatefulRequest(delete, 200, 202);
        Logger.getLogger(http.getHostName()).info("Removed user: " + userName);
    }

    private void sendStatefulRequest(Request request, int... successfulStatusCodes) throws UnauthorizedException, UpdateConflictException, BusinessException, TechnicalException {
        try {
            int responseCode = request.responseCode();
            if (responseCode == 401) {
                throw new UnauthorizedException(request.responseMessage());
            }
            else if (responseCode == 409) {
                throw new UpdateConflictException(request.responseMessage());
            }
            else if (IntStream.of(successfulStatusCodes).noneMatch(n -> n == responseCode)) {
                throw new BusinessException(UsersType.UNEXPECTED, responseCode + ": " + request.responseMessage());
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
    }

}
