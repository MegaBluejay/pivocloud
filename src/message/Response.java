package message;

public class Response {
    public boolean success;
    public String response;

    public Response(boolean success, String response) {
        this.success = success;
        this.response = response;
    }
}
