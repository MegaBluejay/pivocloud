package message;

import server.Server;

public class ShowCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeShow(state);
    }
}
