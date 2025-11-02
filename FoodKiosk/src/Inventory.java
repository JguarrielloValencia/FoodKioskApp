import java.sql.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Inventory {
    // JDBC connection info
    private static final String URL  = "jdbc:mysql://localhost:3306/foodkiosk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "kiosk";       // or "root"
    private static final String PASS = "Kiosk!234";   // your password

    public Inventory() {
        ensureSchema();
    }

    private void ensureSchema() {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement st = c.createStatement()) {

            // 1) Ensure table exists
            st.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id INT PRIMARY KEY,
                category VARCHAR(50) NOT NULL,
                name VARCHAR(100) NOT NULL,
                price DECIMAL(10,2) NOT NULL,
                stock INT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);

            // 2) Ensure 'sold' column exists
            try (PreparedStatement chk = c.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'sold'")) {
                try (ResultSet rs = chk.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        try (Statement alter = c.createStatement()) {
                            alter.execute("ALTER TABLE products ADD COLUMN sold INT NOT NULL DEFAULT 0 AFTER stock");
                            System.out.println("✅ Added missing 'sold' column.");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Schema check failed: " + e.getMessage(), e);
        }
    }

    /** One-time import from products.txt if table empty. Format: id,category,name,price,stock */
    public void importFromFileIfEmpty(String file) {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                if (rs.getInt(1) > 0) return; // already has data
            }
            if (!Files.exists(Path.of(file))) return;

            // ... keep your method signature and earlier code the same ...

            List<String> lines = Files.readAllLines(Path.of(file));
            String sql = """
    INSERT INTO products(id,category,name,price,stock,sold)
    VALUES (?,?,?,?,?,0)
    ON DUPLICATE KEY UPDATE category=VALUES(category), name=VALUES(name),
                            price=VALUES(price), stock=VALUES(stock)
""";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (String raw : lines) {
                    if (raw == null) continue;
                    String s = raw.strip();                  // trim both ends
                    if (s.isEmpty() || s.startsWith("#"))    // allow comments/blank lines
                        continue;

                    // handle possible UTF-8 BOM
                    if (s.length() > 0 && s.charAt(0) == '\uFEFF') s = s.substring(1);

                    String[] p = s.split(",");
                    if (p.length < 5) continue;

                    String idStr = p[0].trim();
                    // skip header or bad first field (not all digits)
                    if (!idStr.matches("\\d+")) continue;

                    try {
                        int id       = Integer.parseInt(idStr);
                        String cat   = p[1].trim();
                        String name  = p[2].trim();
                        double price = Double.parseDouble(p[3].trim());
                        int stock    = Integer.parseInt(p[4].trim());

                        ps.setInt(1, id);
                        ps.setString(2, cat);
                        ps.setString(3, name);
                        ps.setDouble(4, price);
                        ps.setInt(5, stock);
                        ps.addBatch();
                    } catch (NumberFormatException badLine) {
                        // skip just this line; continue importing the rest
                        continue;
                    }
                }
                ps.executeBatch();
            }

        } catch (Exception e) {
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    public List<Product> all() {
        List<Product> out = new ArrayList<>();
        String sql = "SELECT id,category,name,price,stock,sold FROM products ORDER BY id";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Product> find(int id) {
        String sql = "SELECT id,category,name,price,stock,sold FROM products WHERE id=?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void restock(int productId, int qty) {
        String sql = "UPDATE products SET stock = stock + ? WHERE id = ?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, productId);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Product not found: " + productId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Apply sale: decrement stock, increment sold (transactional). */
    public void applySale(Map<Product,Integer> lines) {
        String sql = "UPDATE products SET stock = stock - ?, sold = sold + ? WHERE id = ?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (Map.Entry<Product,Integer> e : lines.entrySet()) {
                ps.setInt(1, e.getValue());
                ps.setInt(2, e.getValue());
                ps.setInt(3, e.getKey().getId());
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Product> topSelling(int n) {
        List<Product> out = new ArrayList<>();
        String sql = "SELECT id,category,name,price,stock,sold FROM products ORDER BY sold DESC, id ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, n));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveToFile(String filename) {
        // no-op; persistence is now MySQL
    }

    private static Product map(ResultSet rs) throws SQLException {
        Product p = new Product(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getDouble("price"),
                rs.getInt("stock")
        );
        // set sold via reflection to keep Product API simple
        try {
            var f = Product.class.getDeclaredField("sold");
            f.setAccessible(true);
            f.setInt(p, rs.getInt("sold"));
        } catch (Exception ignore) {}
        return p;
    }
}
