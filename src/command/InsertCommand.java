package command;

import marine.SpaceMarine;
import server.Server;

public class InsertCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeInsert(this);
    }

    public long key;
    public SpaceMarine marine;

    public InsertCommand(long key, SpaceMarine marine) {
        this.key = key;
        this.marine = marine;
    }
}
