import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Technically this class isn't needed for the project itself / helper class
 * It was to help me learn how to create and connect a database.
 * Basic database utility class for managing the Food Kiosk MySQL schema and performing CRUD operations.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Ensure the {@code products} table exists at startup.</li>
 *   <li>Insert or update product records (with upsert behavior).</li>
 *   <li>Update stock levels.</li>
 *   <li>Retrieve a simple list of products as formatted strings.</li>
 * </ul>
 * <p>
 * This class is a lightweight helper alternative to {@link Inventory}, suitable for early testing,
 * database setup, or basic administration tools.
 *
 * @author Joseph Guarriello
 */
public class Database {
    /** JDBC URL for connecting to the MySQL database. */
    private static final String URL  =
            "jdbc:mysql://localhost:3306/foodkiosk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    /** Database username (e.g., {@code kiosk} or {@code root}). */
    private static final String USER = "kiosk";

    /** Database password corresponding to {@link #USER}. */
    private static final String PASS = "Kiosk!234";

    /**
     * Constructs the {@code Database} helper and ensures required tables exist.
     */
    public Database() {
        createTablesIfNotExist();
    }

    /**
     * Returns a live JDBC connection to the kiosk database.
     * <p>
     * <b>Note:</b> This placeholder currently returns {@code null}.
     * Update it to return {@code DriverManager.getConnection(URL, USER, PASS)} if needed
     * for advanced integration.
     *
     * @return a {@link Connection} to the database, or {@code null} (currently unimplemented)
     */
    public static Connection getConnection() {
        return null;
    }

    /**
     * Creates the {@code products} table if it does not already exist.
     * <p>
     * Columns:
     * <ul>
     *   <li>{@code id} (INT, primary key)</li>
     *   <li>{@code category} (VARCHAR)</li>
     *   <li>{@code name} (VARCHAR)</li>
     *   <li>{@code price} (DOUBLE)</li>
     *   <li>{@code stock} (INT)</li>
     *   <li>{@code sold} (INT, default 0)</li>
     * </ul>
     * The table uses the default InnoDB engine and UTF-8 charset.
     */
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

    /**
     * Inserts or updates a product record in the {@code products} table.
     * <p>
     * If a product with the same ID already exists, its category, name, price,
     * and stock values are updated automatically.
     *
     * @param id       unique product ID
     * @param category product category (e.g., "Italian menu", "Boba Drink", "Dessert")
     * @param name     product name
     * @param price    product price
     * @param stock    quantity in stock
     */
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

    /**
     * Updates the stock quantity for an existing product.
     *
     * @param id    product ID
     * @param stock new stock value
     */
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

    /**
     * Retrieves a list of all products from the database, formatted as human-readable strings.
     * <p>
     * Each row is formatted as:
     * <pre>
     * ID | Category | Name | $Price | Stock: N
     * </pre>
     *
     * @return a list of string summaries for each product
     */
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
