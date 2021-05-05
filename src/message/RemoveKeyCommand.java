package message;

import server.Server;

public class RemoveKeyCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeRemoveKey(currentUser, this);
    }

    public long key;

    public RemoveKeyCommand(long key) {
        this.key = key;
    }
}
