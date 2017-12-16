package de.bornemisza.sessions.da;

import javax.inject.Inject;

import org.javalite.http.Get;
import org.javalite.http.HttpException;

import de.bornemisza.rest.HttpHeaders;
import de.bornemisza.rest.Json;
import de.bornemisza.rest.entity.UuidsResult;
import de.bornemisza.rest.exception.BusinessException;
import de.bornemisza.rest.exception.TechnicalException;
import de.bornemisza.rest.security.Auth;
import de.bornemisza.sessions.boundary.SessionsType;

public class UuidsService {

    public UuidsService() {
    }

    // Constructor for Unit Tests
    public UuidsService(CouchPool couchPool) {
        this.couchPool = couchPool;
    }

    @Inject
    CouchPool couchPool;

    public UuidsResult getUuids(Auth auth, int count) {
        Get get = couchPool.getConnection().getHttp().get("_uuids?count=" + count)
                .header(HttpHeaders.COOKIE, auth.getCookie());
        try {
            int responseCode = get.responseCode();
            if (responseCode != 200) {
                throw new BusinessException(SessionsType.UNEXPECTED, responseCode + ": " + get.responseMessage());
            }
        }
        catch (HttpException ex) {
            throw new TechnicalException(ex.toString());
        }
        return Json.fromJson(get.text(), UuidsResult.class);
    }

}
