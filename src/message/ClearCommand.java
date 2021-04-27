package message;

import server.Server;

public class ClearCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeClear(this);
    }
}
