package message;

import server.Server;

public class ClearCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeClear(state, currentUser);
    }
}
