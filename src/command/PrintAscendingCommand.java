package command;

import server.Server;

public class PrintAscendingCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executePrintAscending(this);
    }
}
