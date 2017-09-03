package de.bornemisza.sessions.endpoint;

import com.hazelcast.core.InitialMembershipEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.security.auth.login.CredentialNotFoundException;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.javalite.http.Get;
import org.javalite.http.Post;

import com.hazelcast.core.InitialMembershipListener;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;

import de.bornemisza.rest.BasicAuthCredentials;
import de.bornemisza.rest.Http;
import de.bornemisza.rest.da.HttpPool;
import de.bornemisza.rest.entity.Session;
import de.bornemisza.sessions.JAXRSConfiguration;

@Path("/")
public class Sessions implements InitialMembershipListener {

    @Resource(name="http/Sessions")
    HttpPool sessionsPool;

    @Resource(name="http/Base")
    HttpPool basePool;

    private Http httpSessions, httpBase;
    private final ObjectMapper mapper = new ObjectMapper();

    public Sessions() { }

    @PostConstruct
    public void init() {
        this.httpSessions = sessionsPool.getConnection();
        this.httpBase = basePool.getConnection();
    }

    // Constructor for Unit Tests
    public Sessions(HttpPool sessionsPool, HttpPool basePool) {
        this.sessionsPool = sessionsPool;
        this.httpSessions = sessionsPool.getConnection();
        this.basePool = basePool;
        this.httpBase = basePool.getConnection();
    }

    @GET
    @Path("new")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNewSession(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        BasicAuthCredentials creds;
        try {
            creds = new BasicAuthCredentials(authHeader);
        }
        catch (CredentialNotFoundException ex) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        Post post = httpSessions.post("")
            .param("name", creds.getUserName())
            .param("password", creds.getPassword());
        if (post.responseCode() != 200) {
            return Response.status(post.responseCode()).entity(post.responseMessage()).build();
        }
        Map<String, List<String>> headers = post.headers();
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("No Cookie!").build();
        }
        else {
            return Response.ok().header(HttpHeaders.SET_COOKIE, cookies.get(0)).build();
        }
    }

    @GET
    @Path("active")
    @Produces(MediaType.APPLICATION_JSON)
    public Session getActiveSession(@HeaderParam(HttpHeaders.COOKIE) String cookie) {
        if (isVoid(cookie)) throw new WebApplicationException(
                Response.status(Status.UNAUTHORIZED).entity("No Cookie!").build());
        Get get = httpSessions.get("")
                .header(HttpHeaders.COOKIE, cookie);
        if (get.responseCode() != 200) {
            throw new WebApplicationException(
                    Response.status(get.responseCode()).entity(get.responseMessage()).build());
        }
        Session session = new Session();
        JsonNode root;
        try {
            root = mapper.readTree(get.text());
        }
        catch (IOException ioe) {
            throw new WebApplicationException(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ioe.toString()).build());
        }
        session.setPrincipal(root.path("userCtx").path("name").asText());
        for (JsonNode node : root.path("userCtx").path("roles")) {
            session.addRole(node.asText());
        }
        List<String> cookies = get.headers().get(HttpHeaders.SET_COOKIE);
        if (! (cookies == null || cookies.isEmpty())) session.setCookie(cookies.iterator().next());
        else session.setCookie(cookie);
        return session;
    }

    @DELETE
    @Path("/")
    public Response deleteCookieInBrowser(@HeaderParam(HttpHeaders.COOKIE) String cookie) {
        return Response.ok()
                .header("Cache-Control", "must-revalidate")
                .header("Set-Cookie", "AuthSession=; Version=1; Path=/; HttpOnly")
                .build();
    }

    @GET
    @Path("uuid")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUuids(@HeaderParam(HttpHeaders.COOKIE) String cookie,
                             @DefaultValue("1")@QueryParam("count") int count) {
        if (isVoid(cookie)) throw new WebApplicationException(
                Response.status(Status.UNAUTHORIZED).entity("No Cookie!").build());
        Get get = httpBase.get("_uuids?count=" + count)
                .header(HttpHeaders.COOKIE, cookie);
        if (get.responseCode() != 200) {
            throw new WebApplicationException(
                    Response.status(get.responseCode()).entity(get.responseMessage()).build());
        }
        return Response.ok().entity(get.text()).build();
    }

    private boolean isVoid(String value) {
        if (value == null) return true;
        else if (value.length() == 0) return true;
        else return value.equals("null");
    }

    @Override
    public void init(InitialMembershipEvent ime) {
        // upon joining the cluster I'll assign myself the next available color
        int clusterSize = ime.getMembers().size();
        if (clusterSize <= JAXRSConfiguration.COLORS.size()) {
            JAXRSConfiguration.MY_COLOR = JAXRSConfiguration.COLORS.get(clusterSize - 1);
        }
    }

    @Override
    public void memberAdded(MembershipEvent me) {
        // nothing
    }

    @Override
    public void memberRemoved(MembershipEvent me) {
        // nothing
    }

    @Override
    public void memberAttributeChanged(MemberAttributeEvent mae) {
        // nothing
    }

}
