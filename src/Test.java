import java.sql.*;

public class Test {

    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/pivo";
        String user = "archer";
        String password = "mmm";

        try (
                Connection con = DriverManager.getConnection(url, user, password);
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT VERSION()")
        ) {
            if (rs.next()) {
                System.out.println(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
