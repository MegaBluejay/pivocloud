package message;

import marine.SpaceMarine;
import server.Server;

public class ReplaceIfLowerCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeReplaceIfLower(this);
    }

    public long key;
    public SpaceMarine marine;

    public ReplaceIfLowerCommand(long key, SpaceMarine marine) {
        this.key = key;
        this.marine = marine;
    }
}
