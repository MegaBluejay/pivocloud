package message;

import server.Server;

public class RemoveLowerKeyCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeRemoveLowerKey(currentUser, this);
    }

    public long key;

    public RemoveLowerKeyCommand(long key) {
        this.key = key;
    }
}
