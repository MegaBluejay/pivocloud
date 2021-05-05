package message;

import marine.SpaceMarine;
import server.Server;

public class ReplaceIfLowerCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeReplaceIfLower(currentUser, this);
    }

    public long key;
    public SpaceMarine marine;

    public ReplaceIfLowerCommand(long key, SpaceMarine marine) {
        this.key = key;
        this.marine = marine;
    }
}
