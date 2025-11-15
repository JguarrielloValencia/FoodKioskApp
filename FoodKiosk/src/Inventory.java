import java.sql.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Inventory data-access layer backed by MySQL.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Create/verify the {@code products} table schema on startup</li>
 *   <li>Optionally import seed data from a CSV-like text file when the table is empty</li>
 *   <li>CRUD-style read operations and small write operations (restock, apply sale)</li>
 *   <li>Projection queries like top-selling products</li>
 * </ul>
 * <p>
 * Table schema (created if missing):
 * <pre>
 * products(
 *   id INT PRIMARY KEY,
 *   category VARCHAR(50) NOT NULL,
 *   name VARCHAR(100) NOT NULL,
 *   price DECIMAL(10,2) NOT NULL,
 *   stock INT NOT NULL,
 *   sold INT NOT NULL DEFAULT 0
 * )
 * </pre>
 *
 * @author Joseph Guarriello
 */
public class Inventory {
    /** JDBC URL for the Food Kiosk database. */
    private static final String URL  = "jdbc:mysql://localhost:3306/foodkiosk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    /** Database username (e.g., {@code kiosk} or {@code root}). */
    private static final String USER = "kiosk";
    /** Database password associated with {@link #USER}. */
    private static final String PASS = "Kiosk!234";

    /**
     * Constructs an {@code Inventory} repo and ensures the schema exists.
     * <p>
     * Invokes {@link #ensureSchema()} which is idempotent and safe to run repeatedly.
     */
    public Inventory() {
        ensureSchema();
    }

    /**
     * Ensures the {@code products} table exists and contains a {@code sold} column.
     * <p>
     * If {@code sold} is missing, it is added with a default of {@code 0}.
     *
     * @throws RuntimeException if schema validation or migrations fail
     */
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

    /**
     * One-time import from a text file <em>if and only if</em> the {@code products} table is empty.
     * <p>
     * Expected CSV-like format per line (header allowed, comments starting with {@code #} are ignored):
     * <pre>id,category,name,price,stock</pre>
     * Non-numeric IDs or malformed lines are skipped. Existing IDs are upserted.
     *
     * @param file path to the seed file (e.g., {@code products.txt})
     * @throws RuntimeException if an IO/JDBC error occurs
     */
    public void importFromFileIfEmpty(String file) {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                if (rs.getInt(1) > 0) return; // already has data
            }
            Path path = Path.of(file);
            if (!Files.exists(path)) return;

            List<String> lines = Files.readAllLines(path);
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
                    if (s.charAt(0) == '\uFEFF') s = s.substring(1);

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
                    }
                }
                ps.executeBatch();
            }

        } catch (Exception e) {
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all products ordered by {@code id}.
     *
     * @return list of all {@link Product} rows
     * @throws RuntimeException on JDBC errors
     */
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

    /**
     * Looks up a product by {@code id}.
     *
     * @param id product identifier
     * @return an {@link Optional} containing the product if found; otherwise empty
     * @throws RuntimeException on JDBC errors
     */
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

    /**
     * Increments the stock of a given product.
     *
     * @param productId product identifier
     * @param qty       quantity to add (maybe negative if you intend to decrement, though not recommended)
     * @throws RuntimeException if the product does not exist or a JDBC error occurs
     */
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

    /**
     * Applies a sale transactionally by decrementing stock and incrementing sold for each line.
     *
     * @param lines map of {@link Product} to quantity sold
     * @throws RuntimeException on JDBC/transaction errors
     */
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

    /**
     * Returns the top {@code n} products sorted by {@code sold} (descending) then {@code id} (ascending).
     *
     * @param n maximum number of rows to return (values &lt; 0 are treated as 0)
     * @return list of top-selling products
     * @throws RuntimeException on JDBC errors
     */
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

    /**
     * No-op because persistence is fully handled by MySQL in this implementation.
     * <p>
     * Kept for API compatibility with the file-based version used by the console app.
     */
    public void saveToFile() {
        // no-op; persistence is now MySQL
    }

    /**
     * Maps a JDBC {@link ResultSet} row to a {@link Product} instance.
     * <p>
     * Note: this uses reflection to populate the {@code sold} field to avoid exposing a public mutator.
     *
     * @param rs positioned result set
     * @return product corresponding to the current row
     * @throws SQLException on JDBC access errors
     */
    private static Product map(ResultSet rs) throws SQLException {
        // Ensure constructor argument order matches your Product class:
        // Product(int id, String category, String name, double price, int stock)
        Product p = new Product(
                rs.getInt("id"),
                rs.getString("category"),
                rs.getString("name"),
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
