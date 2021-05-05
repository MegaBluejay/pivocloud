package message;

import marine.SpaceMarine;
import server.Server;

public class UpdateCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeUpdate(currentUser, this);
    }

    public long id;
    public SpaceMarine marine;

    public UpdateCommand(long id, SpaceMarine marine) {
        this.id = id;
        this.marine = marine;
    }
}
