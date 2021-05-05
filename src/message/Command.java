package message;

import server.ClientState;
import server.Server;

import java.io.Serializable;

public abstract class Command implements Serializable {
    public ClientState state;
    public abstract void execute(Server server, String currentUser);
}
