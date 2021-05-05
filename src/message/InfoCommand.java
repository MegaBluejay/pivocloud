package message;

import server.Server;

public class InfoCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeInfo(state);
    }
}
