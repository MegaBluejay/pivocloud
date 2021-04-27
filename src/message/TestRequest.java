package message;

import server.Server;

public class TestRequest extends Request {

    public TestRequest(String user, String passHash) {
        super(user, passHash);
    }

    @Override
    public void handle(Server server) {
        server.handleTestRequest(this);
    }
}
