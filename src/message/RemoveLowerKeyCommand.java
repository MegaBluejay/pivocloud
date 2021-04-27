package message;

import server.Server;

public class RemoveLowerKeyCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeRemoveLowerKey(this);
    }

    public long key;

    public RemoveLowerKeyCommand(long key) {
        this.key = key;
    }
}
