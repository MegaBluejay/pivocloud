package message;

public class RequestFactory {
    private final String user;
    private final String passHash;

    public RequestFactory(String user, String passHash) {
        this.user = user;
        this.passHash = passHash;
    }

    public Request request(Command command) {
        return new NormalRequest(user, passHash, command);
    }
}
