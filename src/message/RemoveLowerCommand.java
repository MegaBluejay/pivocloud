package message;

import marine.SpaceMarine;
import server.Server;

public class RemoveLowerCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeRemoveLower(currentUser, this);
    }

    public SpaceMarine marine;

    public RemoveLowerCommand(SpaceMarine marine) {
        this.marine = marine;
    }
}
