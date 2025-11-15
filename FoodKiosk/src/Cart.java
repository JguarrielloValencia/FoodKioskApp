import java.util.LinkedHashMap;
import java.util.Map;

/* You are not expected to understand this –Dennis Richie's—yes, that Dennis Richie!— Odd Comments and Strange Doings in Unix*/

/**
 * Represents a customer's shopping cart in the Food-Type Kiosk system.
 * <p>
 * The cart stores {@link Product} objects and their quantities
 * using a {@link LinkedHashMap} to preserve insertion order.
 * It provides methods to add products, compute the subtotal,
 * and check or clear contents.
 *
 * @author Joseph Guarriello
 */
public class Cart {
    /** Map of products and their associated quantities. */
    private final Map<Product, Integer> lines = new LinkedHashMap<>();

    /**
     * Adds a product to the cart, merging quantities if the product already exists.
     *
     * @param p   the product to add
     * @param qty the quantity to add (must be positive)
     * @throws IllegalArgumentException if {@code qty <= 0}
     */
    public void add(Product p, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        lines.merge(p, qty, Integer::sum);
    }

    /**
     * Calculates the subtotal (total cost of all items in the cart).
     *
     * @return total price of all products in the cart
     */
    public double subtotal() {
        return lines.entrySet().stream()
                .mapToDouble(e -> e.getKey().getPrice() * e.getValue())
                .sum();
    }

    /**
     * Returns a view of the product–quantity map for iteration or display.
     *
     * @return map of {@link Product} to quantity
     */
    public Map<Product, Integer> lines() {
        return lines;
    }

    /**
     * Checks whether the cart is empty.
     *
     * @return {@code true} if the cart has no items; {@code false} otherwise
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Clears all items from the cart.
     */
    public void clear() {
        lines.clear();
    }
}
