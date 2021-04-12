package command;

import marine.SpaceMarine;
import server.Server;

public class RemoveLowerCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeRemoveLower(this);
    }

    public SpaceMarine marine;

    public RemoveLowerCommand(SpaceMarine marine) {
        this.marine = marine;
    }
}
