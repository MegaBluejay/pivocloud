package message;

import server.Server;

public class GroupCountingByCreationDateCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeGroupCountingByCreationDate(this);
    }
}
