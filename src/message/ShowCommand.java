package message;

import server.Server;

public class ShowCommand implements Command{
    @Override
    public void execute(Server server) {
        server.executeShow(this);
    }
}
