package message;

import server.Server;

import java.io.Serializable;

public abstract class Request implements Serializable {
    public String user;
    public String passHash;

    public Request(String user, String passHash) {
        this.user = user;
        this.passHash = passHash;
    }

    public abstract void handle(Server server);
}
