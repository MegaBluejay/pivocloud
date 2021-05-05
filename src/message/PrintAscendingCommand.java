package message;

import server.Server;

public class PrintAscendingCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executePrintAscending(state);
    }
}
