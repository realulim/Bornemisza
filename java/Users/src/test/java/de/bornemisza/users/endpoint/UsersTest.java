package de.bornemisza.users.endpoint;

import javax.ws.rs.WebApplicationException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

import de.bornemisza.users.boundary.UsersFacade;
import de.bornemisza.users.entity.User;

public class UsersTest {

    private UsersFacade facade;

    private Users CUT;
    
    public UsersTest() {
    }
    
    @Before
    public void setUp() {
        facade = mock(UsersFacade.class);
        CUT = new Users(facade);
    }
    
    @Test
    public void getUser_technicalException() {
        when(facade.getUser(anyString())).thenThrow(new RuntimeException("Some technical problem..."));
        try {
            CUT.getUser("Ike");
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(500, ex.getResponse().getStatus());
        }
    }

    @Test
    public void getUser_userNotFound() {
        when(facade.getUser(anyString())).thenReturn(null);
        try {
            CUT.getUser("Ike");
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(404, ex.getResponse().getStatus());
        }
    }

    @Test
    public void createUser_technicalException() {
        when(facade.createUser(any(User.class))).thenThrow(new RuntimeException("Some technical problem..."));
        try {
            CUT.createUser(new User());
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(500, ex.getResponse().getStatus());
        }
    }

    @Test
    public void createUser_nullUser() {
        try {
            CUT.createUser(null);
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(400, ex.getResponse().getStatus());
        }
    }

    @Test
    public void createUser_userAlreadyExists() {
        when(facade.createUser(any(User.class))).thenReturn(null);
        try {
            CUT.createUser(new User());
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(409, ex.getResponse().getStatus());
        }
    }

    @Test
    public void updateUser_technicalException() {
        when(facade.updateUser(any(User.class))).thenThrow(new RuntimeException("Some technical problem..."));
        try {
            CUT.updateUser(new User());
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(500, ex.getResponse().getStatus());
        }
    }

    @Test
    public void updateUser_nullUser() {
        try {
            CUT.updateUser(null);
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(400, ex.getResponse().getStatus());
        }
    }

    @Test
    public void updateUser_newerRevisionAlreadyExists() {
        when(facade.updateUser(any(User.class))).thenReturn(null);
        try {
            CUT.updateUser(new User());
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(409, ex.getResponse().getStatus());
        }
    }

    @Test
    public void deleteUser_technicalException() {
        when(facade.deleteUser(anyString(), anyString())).thenThrow(new RuntimeException("Some technical problem..."));
        when(facade.getUser(anyString())).thenReturn(new User());
        try {
            CUT.deleteUser("Ike", "some revision");
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(500, ex.getResponse().getStatus());
        }
    }

    @Test
    public void deleteUser_nullUser() {
        when(facade.getUser(anyString())).thenReturn(null);
        try {
            CUT.deleteUser("Ike", "some revision");
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(404, ex.getResponse().getStatus());
        }
    }

    @Test
    public void deleteUser_newerRevisionAlreadyExists() {
        when(facade.getUser(anyString())).thenReturn(new User());
        when(facade.deleteUser(anyString(), anyString())).thenReturn(false);
        try {
            CUT.deleteUser("Ike", "some revision");
            fail();
        }
        catch (WebApplicationException ex) {
            assertEquals(409, ex.getResponse().getStatus());
        }
    }

}