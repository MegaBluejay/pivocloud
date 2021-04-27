package message;

import server.Server;

public class NormalRequest extends Request {

    public Command command;

    public NormalRequest(String user, String passHash, Command command) {
        super(user, passHash);
        this.command = command;
    }

    @Override
    public void handle(Server server) {
        server.handleNormalRequest(this);
    }
}
