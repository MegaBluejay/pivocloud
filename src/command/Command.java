package command;

import server.Server;

import java.io.Serializable;

public interface Command extends Serializable {
    void execute(Server server);
}
