package command;

import marine.AstartesCategory;
import server.Server;

public class FilterGreaterThanCategoryCommand implements Command {
    @Override
    public void execute(Server server) {
        server.executeFilterGreaterThanCategory(this);
    }

    public AstartesCategory category;

    public FilterGreaterThanCategoryCommand(AstartesCategory category) {
        this.category = category;
    }
}
