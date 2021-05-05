package message;

import marine.AstartesCategory;
import server.Server;

public class FilterGreaterThanCategoryCommand extends Command {
    @Override
    public void execute(Server server, String currentUser) {
        server.executeFilterGreaterThanCategory(this);
    }

    public AstartesCategory category;

    public FilterGreaterThanCategoryCommand(AstartesCategory category) {
        this.category = category;
    }
}
