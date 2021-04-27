package message;

import server.Server;

public class RemoveKeyCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeRemoveKey(this);
    }

    public long key;

    public RemoveKeyCommand(long key) {
        this.key = key;
    }
}
