package de.bornemisza.users.endpoint;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

import de.bornemisza.users.IntegrationTestBase;
import de.bornemisza.users.boundary.BasicAuthCredentials;
import de.bornemisza.users.entity.User;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UsersIT extends IntegrationTestBase {

    private static String revision, derivedKey, salt;

    @Test
    public void t00_userAccountCreationRequest() {
        deleteMails(user.getEmail());
        user.setPassword(userPassword.toCharArray());
        Response response = postUser(user, 202);
        assertEquals(0, response.getBody().prettyPrint().length());
    }

    @Test
    public void t01_confirmUser() {
        long start = System.currentTimeMillis();
        String confirmationLink = retrieveConfirmationLink(user.getEmail());
        assertTrue(confirmationLink.startsWith("https://"));
        System.out.println("Mail delivered after " + (System.currentTimeMillis() - start) + " ms.");
        Response response = clickConfirmationLink(confirmationLink, 200);
        JsonPath jsonPath = response.jsonPath();
        assertEquals(user.getEmail().toString(), jsonPath.getString("email"));
        assertNull(jsonPath.getString("password"));

        /* Request is removed from Map by either Expiry or previous Confirmation, so this must fail */
        response = clickConfirmationLink(confirmationLink, 404);
        assertEquals("User Account Creation Request does not exist - maybe expired?", response.print());
    }

    @Test
    public void t02_userAccountCreationRequest_userAlreadyExists() {
        user.setPassword(userPassword.toCharArray());
        Response response = postUser(user, 409);
        assertEquals(user.getName() + " already exists!", response.getBody().print());
    }

    @Test
    public void t03_userAccountCreationRequest_emailAlreadyExists() {
        User userWithExistingEmail = new User();
        userWithExistingEmail.setEmail(user.getEmail());
        userWithExistingEmail.setName("Not " + user.getName());
        userWithExistingEmail.setPassword(new char[] {'p', 'w'});
        userWithExistingEmail.setRoles(user.getRoles());
        Response response = postUser(userWithExistingEmail, 409);
        assertEquals(user.getEmail().getAddress() + " already exists!", response.getBody().print());
    }

    @Test
    public void t04_readUser_notAuthorized() {
        getUser(userName, 401);
    }

    @Test
    public void t05_readUser_unauthorized() {
        requestSpec.auth().preemptive().basic("root", "guessedpassword");
        getUser(userName, 401);
    }

    @Test
    public void t06_readUser() {
        requestSpec.auth().preemptive().basic(userName, userPassword);
        Response response = getUser(userName, 200);
        JsonPath jsonPath = response.jsonPath();
        revision = jsonPath.getString("_rev");
        assertTrue(revision.length() > 10);
        derivedKey = jsonPath.getString("derived_key");
        assertTrue(derivedKey.length() > 10);
        salt = jsonPath.getString("salt");
        assertTrue(salt.length() > 10);
    }

    @Test
    public void t07_changePassword() {
        requestSpec.auth().preemptive().basic(userName, userPassword);
        Response response = changePassword(userName, newUserPassword, 200);
        JsonPath jsonPath = response.getBody().jsonPath();
        String newRevision = jsonPath.getString("_rev");
        assertNotEquals(newRevision, revision);
        revision = newRevision;
    }

    @Test
    public void t08_changeEmailRequest() {
        requestSpec.auth().preemptive().basic(userName, newUserPassword);
        deleteMails(newEmail);
        user.setEmail(newEmail);
        Response response = putEmail(user, 202);
        assertEquals(0, response.getBody().prettyPrint().length());
    }

    @Test
    public void t09_confirmEmail() {
        BasicAuthCredentials creds = new BasicAuthCredentials(userName, newUserPassword);
        long start = System.currentTimeMillis();
        String confirmationLink = retrieveConfirmationLink(newEmail);
        assertTrue(confirmationLink.startsWith("https://"));
        System.out.println("Mail delivered after " + (System.currentTimeMillis() - start) + " ms.");
        Response response = clickConfirmationLink(confirmationLink, 200, creds);
        JsonPath jsonPath = response.jsonPath();
        assertEquals(newEmail.toString(), jsonPath.getString("email"));

        /* Request is removed from Map by either Expiry or previous Confirmation, so this must fail */
        response = clickConfirmationLink(confirmationLink, 404, creds);
        assertEquals("E-Mail Change Request does not exist - maybe expired?", response.print());
    }

    @Test
    public void t10_removeUser() {
        requestSpec.auth().preemptive().basic(userName, newUserPassword);
        deleteUser(userName, 204);
        getUser(userName, 401);
    }

    @Test
    public void t11_checkUserRemoved() {
        requestSpec.auth().preemptive().basic(adminUserName, adminPassword);
        getUser(userName, 404);
    }

    @Test
    public void t12_removeNonExistingUser() {
        requestSpec.auth().preemptive().basic(adminUserName, adminPassword);
        deleteUser(userName, 404);
    }

}
