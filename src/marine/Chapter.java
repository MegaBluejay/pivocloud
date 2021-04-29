package marine;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.postgresql.util.PGobject;
import org.postgresql.util.PGtokenizer;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Objects;

public class Chapter extends PGobject implements Serializable, Cloneable {
    private String name; //Поле не может быть null, Строка не может быть пустой
    private String world; //Поле может быть null

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public Chapter(String name, String world) {
        this();
        this.name = name;
        this.world = world;
    }

    public Chapter() {
        type = "chapter";
    }

    public boolean isNull = false;

    @Override
    public void setValue(String value) throws SQLException {
        if (value == null) {
            isNull = true;
            return;
        }
        PGtokenizer t = new PGtokenizer(PGtokenizer.removePara(value), ',');
        name = t.getToken(0);
        if (name.isEmpty()) {
            throw new SQLException();
        }
        name = name.replaceAll("^\"|\"$", "");
        world = t.getToken(1);
        if (world.isEmpty()) {
            world = null;
        } else {
            world = world.replaceAll("^\"|\"$", "");
        }
    }

    private static String quoted(String s) {
        if (s == null) {
            return "";
        }
        return "\"" + s.replaceAll("\"", "\\\"") + "\"";
    }

    @Override
    public String getValue() {
        if (isNull) {
            return null;
        }
        return "(" + quoted(name) + "," + quoted(world) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Chapter chapter = (Chapter) o;
        return name.equals(chapter.name) && Objects.equals(world, chapter.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, world);
    }

    @Override
    public Object clone() {
        return new Chapter(name, world);
    }
}
