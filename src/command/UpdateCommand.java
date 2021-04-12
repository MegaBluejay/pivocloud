package command;

import marine.SpaceMarine;
import server.Server;

public class UpdateCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeUpdate(this);
    }

    public long id;
    public SpaceMarine marine;

    public UpdateCommand(long id, SpaceMarine marine) {
        this.id = id;
        this.marine = marine;
    }
}
