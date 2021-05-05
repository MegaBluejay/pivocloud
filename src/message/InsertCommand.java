package message;

import marine.SpaceMarine;
import server.Server;

public class InsertCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeInsert(currentUser, this);
    }

    public long key;
    public SpaceMarine marine;

    public InsertCommand(long key, SpaceMarine marine) {
        this.key = key;
        this.marine = marine;
    }
}
