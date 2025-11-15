import java.util.Objects;

/**
 * Represents a product in the Food-Type Kiosk system.
 * <p>
 * A product has identifying information (ID, name, category),
 * pricing data, and simple stock/sales counters.
 * Instances are primarily read from or written to the database
 * through the {@link Inventory} class.
 *
 * @author Joseph Guarriello
 */
public class Product {
    /** Unique numeric identifier for the product. */
    private final int id;
    /** Display name of the product. */
    private final String name;
    /** Product category, e.g., "Boba Drink", "Dessert". */
    private final String category;
    /** Price in U.S. dollars. */
    private final double price;
    /** Current quantity in stock. */
    private int stock;
    /** Cumulative quantity sold. */
    private int sold;

    /**
     * Constructs a product with the specified properties.
     *
     * @param id        unique identifier
     * @param name      product name
     * @param category  category name
     * @param price     product price (must be non-negative)
     * @param stock     current stock count (must be non-negative)
     * @throws IllegalArgumentException if {@code price < 0} or {@code stock < 0}
     */
    public Product(int id, String name, String category, double price, int stock) {
        if (price < 0 || stock < 0)
            throw new IllegalArgumentException("Price/stock cannot be negative");
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.sold = 0;
    }

    /** @return product ID */
    public int getId() { return id; }

    /** @return product name */
    public String getName() { return name; }

    /** @return product category */
    public String getCategory() { return category; }

    /** @return product price */
    public double getPrice() { return price; }

    /** @return number of items currently in stock */
    public int getStock() { return stock; }

    /** @return total number of units sold (local counter) */
    public int getSold() { return sold; }

    // -------------------- Local Stock Operations --------------------

    /**
     * Increases stock by the specified amount.
     * Intended for local (non-database) updates.
     *
     * @param qty amount to add (must be positive)
     * @throws IllegalArgumentException if {@code qty <= 0}
     */
    public void restock(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Restock must be positive");
        stock += qty;
    }

    /**
     * Decreases stock and increments sold count.
     * Used when a sale occurs.
     *
     * @param qty amount sold (must be positive and ≤ current stock)
     * @throws IllegalArgumentException if {@code qty <= 0}
     * @throws IllegalStateException    if {@code qty > stock}
     */
    public void consume(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Quantity must be positive");
        if (qty > stock)
            throw new IllegalStateException("Not enough stock");
        stock -= qty;
        sold += qty;
    }

    // -------------------- Equality / Hashing --------------------

    /**
     * Products are considered equal if they share the same ID.
     *
     * @param o object to compare
     * @return {@code true} if IDs match; otherwise {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product p)) return false;
        return id == p.id;
    }

    /** @return hash code based on product ID */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
