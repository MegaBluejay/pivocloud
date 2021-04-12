package command;

import server.Server;

public class InfoCommand implements Command{
    @Override
    public void execute(Server server) {
        server.executeInfo(this);
    }
}
