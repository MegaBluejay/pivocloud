package message;

import server.Server;

public class RegisterRequest extends Request {

    public RegisterRequest(String user, String passHash) {
        super(user, passHash);
    }

    @Override
    public void handle(Server server) {
        server.handleRegisterRequest(this);
    }
}
