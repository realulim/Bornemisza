package de.bornemisza.users.da.couchdb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.ektorp.CouchDbConnector;
import org.ektorp.DbAccessException;

public class HealthChecks {

    public HealthChecks() {
    }

    public boolean isHostAvailable(final String hostname, final int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostname, port), 1000);
            return true;
        } 
        catch (IOException ex) {
            return false;
        }
    }

    public boolean isCouchDbAvailable(CouchDbConnector db) {
        try {
            db.getDbInfo();
            return true;
        }
        catch (DbAccessException e) {
            return false;
        }
    }

}
