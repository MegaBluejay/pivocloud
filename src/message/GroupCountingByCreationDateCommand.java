package message;

import server.Server;

public class GroupCountingByCreationDateCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeGroupCountingByCreationDate(state);
    }
}
