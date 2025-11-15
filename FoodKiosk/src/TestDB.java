import java.sql.*;

/**
 * This is technically not needed as well, this was just to help me show my database was successful.
 * Simple standalone test to verify that the MySQL database connection works.
 * <p>
 * This class attempts to connect to the {@code foodkiosk} database using JDBC
 * credentials and executes a lightweight test query ({@code SELECT 1}).
 * <p>
 * If successful, a success message is printed; otherwise, the stack trace is shown.
 * <p>
 * Usage:
 * <pre>
 *   javac TestDB.java
 *   java -cp .;mysql-connector-j.jar TestDB
 * </pre>
 * Ensure the MySQL JDBC driver (Connector/J) is in classpath.
 *
 * @author Joseph Guarriello
 */
public class TestDB {

    /**
     * Entry point for the database connection test.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        // JDBC connection details
        String url  = "jdbc:mysql://localhost:3306/foodkiosk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String user = "kiosk";          // or "root"
        String pass = "Kiosk!234";      // update accordingly

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("✅ Connected successfully!");
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                ps.execute();
            }
        } catch (SQLException e) {
            System.out.println("❌ Connection failed:");
            e.printStackTrace();
        }
    }
}
