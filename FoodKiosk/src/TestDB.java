import java.sql.*;

public class TestDB {
    public static void main(String[] args) {
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
