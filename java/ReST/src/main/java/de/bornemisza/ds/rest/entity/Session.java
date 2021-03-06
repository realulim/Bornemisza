package de.bornemisza.ds.rest.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.bornemisza.ds.rest.security.DoubleSubmitToken;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Session extends Document {

    public Session() {
        // JAXB needs this
        super();
        this.type = "session";
    }

    @JsonProperty(value = "name")
    private String principal;

    @JsonProperty(value = "roles")
    private final List<String> roles = new ArrayList<>();

    @JsonIgnore
    private DoubleSubmitToken dsToken;

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String name) {
        this.principal = name;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void addRole(String role) {
        this.roles.add(role);
    }

    public DoubleSubmitToken getDoubleSubmitToken() {
        return dsToken;
    }

    public void setDoubleSubmitToken(DoubleSubmitToken dsToken) {
        this.dsToken = dsToken;
    }

}
