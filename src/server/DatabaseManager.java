package server;

import marine.*;
import org.postgresql.PGConnection;
import org.postgresql.geometric.PGpoint;

import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseManager {

    private Map<String, String> users;
    private Map<Long, SpaceMarine> marines;
    Connection con;

    private String currentUser;

    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement removeKeyStatement;
    private final PreparedStatement clearStatement;
    private final PreparedStatement removeLowerStatement;
    private final PreparedStatement removeLowerKeyStatement;
    private final PreparedStatement adduserStatement;
    private final PreparedStatement syncMarinesStatement;
    private final PreparedStatement syncUsersStatement;

    private boolean isCurrentUsers(SpaceMarine marine) {
        return marine.getOwner().equals(currentUser);
    }

    public void setCurrentUser(String user) {
        currentUser = user;
    }

    public boolean haveUser() {
        return users.containsKey(currentUser);
    }

    public boolean validCreds (String passHash) {
        return haveUser() && users.get(currentUser).equals(passHash);
    }

    public DatabaseManager(String url, String user, String password) throws SQLException {
        con = DriverManager.getConnection(url, user, password);
        ((PGConnection) con).addDataType("chapter", Chapter.class);

        insertStatement = con.prepareStatement(
                "INSERT INTO marines (k, owner, name, coords, date, health, category, weapon, melee, chapter) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id");
        updateStatement = con.prepareStatement(
                "UPDATE marines SET name = ?, coords = ?, health = ?, category = ?, weapon = ?, melee = ?, chapter = ?" +
                        "WHERE k = ?");
        removeKeyStatement = con.prepareStatement(
                "DELETE FROM marines WHERE k = ?");
        clearStatement = con.prepareStatement(
                "DELETE FROM marines WHERE owner = ?");
        removeLowerStatement = con.prepareStatement(
                "DELETE FROM marines WHERE health < ? AND owner = ?");
        removeLowerKeyStatement = con.prepareStatement(
                "DELETE FROM marines WHERE k < ? AND owner = ?");
        adduserStatement = con.prepareStatement(
                "INSERT INTO users (name, hash) VALUES (?, ?)");
        syncMarinesStatement = con.prepareStatement(
                "SELECT k, id, owner, name, coords, date, health, category, weapon, melee, chapter FROM marines"
        );
        syncUsersStatement = con.prepareStatement(
                "SELECT name, hash from users");

        sync();
    }

    private SpaceMarine extractMarine(ResultSet set) throws SQLException {
        Long id = set.getLong("id");
        String owner = set.getString("owner");
        String name = set.getString("name");
        PGpoint sqlPoint = (PGpoint) set.getObject("coords");
        Coordinates coords = new Coordinates(sqlPoint.x, sqlPoint.y);
        java.sql.Date sqlDate = set.getDate("date");
        LocalDate date = sqlDate.toLocalDate();
        float health = set.getFloat("health");
        String catString = set.getString("category");
        AstartesCategory category = catString == null ? null : AstartesCategory.valueOf(catString);
        Weapon weapon = Weapon.valueOf(set.getString("weapon"));
        MeleeWeapon meleeWeapon = MeleeWeapon.valueOf(set.getString("melee"));
        Chapter chapter = (Chapter) set.getObject("chapter");
        return new SpaceMarine(id, name, coords, date, health, category, weapon, meleeWeapon, chapter, owner);
    }

    private void sync() throws SQLException {
        users = new HashMap<>();
        marines = new HashMap<>();
        ResultSet marineResults = syncMarinesStatement.executeQuery();
        while (marineResults.next()) {
            Long key = marineResults.getLong("k");
            SpaceMarine marine = extractMarine(marineResults);
            marines.put(key, marine);
        }
        ResultSet userResults = syncUsersStatement.executeQuery();
        while (userResults.next()) {
            String user = userResults.getString("name");
            String passHash = userResults.getString("hash");
            users.put(user, passHash);
        }
    }

    public ManagerAnswer addUser(String passHash) {
        if (users.containsKey(currentUser)) {
            return ManagerAnswer.BAD_OP;
        }
        try {
            adduserStatement.setString(1, currentUser);
            adduserStatement.setString(2, passHash);

            adduserStatement.execute();

            users.put(currentUser, passHash);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public MarineInfo info() {
        String type = "HashMap<Long, SpaceMarine>";
        int n = marines.size();
        LocalDate date = marines.values()
                .stream().map(SpaceMarine::getCreationDate).max(Comparator.naturalOrder()).orElse(null);
        return new MarineInfo(type, n, date);
    }

    public ManagerAnswer insert(Long key, SpaceMarine marine) {
        if (marines.containsKey(key)) {
            return ManagerAnswer.BAD_OP;
        }
        try {
            insertStatement.setLong(1, key);
            insertStatement.setString(2, currentUser);
            insertStatement.setString(3, marine.getName());
            PGpoint sqlPoint = new PGpoint(marine.getCoordinates().getX(), marine.getCoordinates().getY());
            insertStatement.setObject(4, sqlPoint);
            LocalDate date = LocalDate.now();
            Date sqlDate = Date.valueOf(date);
            insertStatement.setObject(5, sqlDate);
            insertStatement.setFloat(6, marine.getHealth());
            insertStatement.setObject(7, marine.getCategory(), Types.OTHER);
            insertStatement.setObject(8, marine.getWeaponType(), Types.OTHER);
            insertStatement.setObject(9, marine.getMeleeWeapon(), Types.OTHER);
            insertStatement.setObject(10, marine.getChapter());

            ResultSet rs = insertStatement.executeQuery();

            rs.next();
            Long id = rs.getLong("id");

            marine.setId(id);
            marine.setCreationDate(date);
            marines.put(key, marine);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public List<Map.Entry<Long, SpaceMarine>> list() {
        return new ArrayList<>(marines.entrySet());
    }

    public ManagerAnswer update(Long id, SpaceMarine marine) {
        Optional<Long> mbKey = marines.keySet()
                .stream().filter(k -> marines.get(k).getId().equals(id))
                .findAny();
        if (!mbKey.isPresent()) {
            return ManagerAnswer.BAD_OP;
        }
        Long key = mbKey.get();
        SpaceMarine old = marines.get(key);
        if (!isCurrentUsers(old)) {
            return ManagerAnswer.BAD_OWNER;
        }
        marine.setId(id);
        marine.setCreationDate(old.getCreationDate());
        try {
            updateStatement.setString(1, marine.getName());
            PGpoint sqlPoint = new PGpoint(marine.getCoordinates().getX(), marine.getCoordinates().getY());
            updateStatement.setObject(2, sqlPoint);
            updateStatement.setFloat(3, marine.getHealth());
            updateStatement.setObject(4, marine.getCategory(), Types.OTHER);
            updateStatement.setObject(5, marine.getWeaponType(), Types.OTHER);
            updateStatement.setObject(6, marine.getMeleeWeapon(), Types.OTHER);
            updateStatement.setObject(7, marine.getChapter());
            updateStatement.setLong(8, key);

            updateStatement.execute();

            marines.put(key, marine);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public ManagerAnswer removeKey(Long key) {
        if (!marines.containsKey(key)) {
            return ManagerAnswer.BAD_OP;
        }
        if (!isCurrentUsers(marines.get(key))) {
            return ManagerAnswer.BAD_OWNER;
        }
        try {
            removeKeyStatement.setLong(1, key);

            removeKeyStatement.execute();

            marines.remove(key);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public ManagerAnswer clear() {
        try {
            clearStatement.setString(1, currentUser);

            clearStatement.execute();

            marines.keySet()
                    .stream().filter(k -> isCurrentUsers(marines.get(k)))
                    .forEach(marines::remove);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public ManagerAnswer removeLower(SpaceMarine marine) {
        try {
            removeLowerStatement.setFloat(1, marine.getHealth());
            removeLowerStatement.setString(2, currentUser);

            removeLowerStatement.execute();

            marines.keySet()
                    .stream().filter(k -> marines.get(k).compareTo(marine) < 0)
                    .filter(k -> isCurrentUsers(marines.get(k)))
                    .forEach(marines::remove);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public ManagerAnswer replaceIfLower(Long key, SpaceMarine marine) {
        if (!marines.containsKey(key)) {
            return ManagerAnswer.BAD_OP;
        }
        SpaceMarine old = marines.get(key);
        if (!isCurrentUsers(old)) {
            return ManagerAnswer.BAD_OWNER;
        }
        if (old.compareTo(marine) < 0) {
            return update(old.getId(), marine);
        }
        return ManagerAnswer.OK;
    }

    public ManagerAnswer removeLowerKey(Long key) {
        try {
            removeLowerKeyStatement.setLong(1, key);
            removeLowerKeyStatement.setString(2, currentUser);

            removeLowerKeyStatement.execute();

            marines.keySet()
                    .stream().filter(k -> k < key)
                    .filter(k -> isCurrentUsers(marines.get(k)))
                    .forEach(marines::remove);
            return ManagerAnswer.OK;
        } catch (SQLException throwables) {
            return ManagerAnswer.DB_ERROR;
        }
    }

    public Map<LocalDate, Long> groupCountingByCreationDate() {
        return marines.values()
                .stream().collect(Collectors.groupingBy(
                        SpaceMarine::getCreationDate, Collectors.counting()));
    }

    public List<Map.Entry<Long, SpaceMarine>> filterGreaterThanCategory(AstartesCategory category) {
        return marines.entrySet()
                .stream().filter(e -> {
                    AstartesCategory cat = e.getValue().getCategory();;
                    return cat != null && cat.ordinal() > category.ordinal();
                }).collect(Collectors.toList());
    }

    public List<Map.Entry<Long, SpaceMarine>> ascending() {
        return marines.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
    }
}
