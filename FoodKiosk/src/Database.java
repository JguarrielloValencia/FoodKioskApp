import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final String URL  = "jdbc:mysql://localhost:3306/foodkiosk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "kiosk";        // or "root"
    private static final String PASS = "Kiosk!234";    // change if needed

    public Database() {
        createTablesIfNotExist();
    }

    public static Connection getConnection() {
        return null;
    }

    private void createTablesIfNotExist() {
        String sqlProducts = """
            CREATE TABLE IF NOT EXISTS products (
                id INT PRIMARY KEY,
                category VARCHAR(50),
                name VARCHAR(100),
                price DOUBLE,
                stock INT,
                sold INT DEFAULT 0
            )
        """;
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlProducts);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------- Insert / Update ----------
    public void insertProduct(int id, String category, String name, double price, int stock) {
        String sql = "INSERT INTO products (id, category, name, price, stock) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE category=?, name=?, price=?, stock=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, category);
            ps.setString(3, name);
            ps.setDouble(4, price);
            ps.setInt(5, stock);
            ps.setString(6, category);
            ps.setString(7, name);
            ps.setDouble(8, price);
            ps.setInt(9, stock);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateStock(int id, int stock) {
        String sql = "UPDATE products SET stock=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stock);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------- Select ----------
    public List<String> listProducts() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY id";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String row = String.format("%d | %s | %s | $%.2f | Stock: %d",
                        rs.getInt("id"),
                        rs.getString("category"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"));
                list.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
