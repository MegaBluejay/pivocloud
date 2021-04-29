import marine.*;
import server.DatabaseManager;

import java.sql.SQLException;

public class Test {

    public static void main(String[] args) throws SQLException{
        String url = "jdbc:postgresql://localhost:5432/pivo";
        String user = "archer";
        String password = "mmm";

        DatabaseManager manager = new DatabaseManager(url, user, password);

        manager.setCurrentUser("jay");

//        System.out.println(manager.update(2L, new SpaceMarine(
//                null,
//                "newer name",
//                new Coordinates(1.0, 1.0),
//                null,
//                10.0f,
//                null,
//                Weapon.BOLTGUN,
//                MeleeWeapon.MANREAPER,
//                new Chapter("cname with spaces", null),
//                "jay"
//        )));
        System.out.println(manager.list().get(0).getValue().getChapter());
    }
}
