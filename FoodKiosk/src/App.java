import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
// Sorry for what you are about to see –John Vester’s Code Commenting Patterns
/**
 * <h1>Main Program to find food kiosk and add them to cart!</h1>
 * Console-based "Food-Type Kiosk" application.
 * <p>
 * Features:
 * <ul>
 *   <li><b>Customer mode</b>: list items, add to cart, view cart, checkout</li>
 *   <li><b>Admin mode</b>: view inventory, restock products, view top sellers</li>
 *   <li>Inventory persists to a simple text file on exit via a shutdown hook</li>
 * </ul>
 * <p>
 * This class coordinates high-level I/O flow and delegates data operations to
 * {@code Inventory}, {@code Product}, and {@code Cart}.
 *
 * @author Joseph Guarriello
 */
public class App {
    /** Shared scanner for console input. */
    private static final Scanner in = new Scanner(System.in);
    /** Demo admin PIN. Do not use in production. */
    private static final String ADMIN_PIN = "1234";
    /** Inventory seed/backup file name. */
    private static final String INVENTORY_FILE = "products.txt";

    /**
     * Application entry point.
     *
     * @param args CLI args (unused)
     */
    public static void main(String[] args) {
        Inventory inventory = seed();

        // Persist on exit regardless of where we return
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { inventory.saveToFile(); } catch (Exception ignored) {}
        }));

        println("\n=== Food-Type Kiosk (Console) ===");
        while (true) {
            println("""
                    
                    Choose mode:
                      1) Customer
                      2) Admin
                      0) Exit
                    """);
            String sel = prompt("Select");
            switch (sel) {
                case "1" -> runCustomer(inventory);
                case "2" -> runAdmin(inventory);
                case "0" -> {
                    println("Bye!");
                    return;
                }
                default -> println("Invalid option.");
            }
        }
    }

    // -------------------- Customer --------------------

    /**
     * Runs the customer interaction loop.
     *
     * @param inv active inventory instance
     */
    private static void runCustomer(Inventory inv) {
        Cart cart = new Cart();
        while (true) {
            println("""
                    
                    Customer Menu
                      1) List items
                      2) Add to cart
                      3) View cart
                      4) Checkout
                      0) Back
                    """);
            String choice = prompt("Select");
            switch (choice) {
                case "1" -> listItems(inv);
                case "2" -> addToCart(inv, cart);
                case "3" -> viewCart(cart);
                case "4" -> checkout(inv, cart);
                case "0" -> { return; }
                default -> println("Invalid option.");
            }
        }
    }

    // -------------------- Admin --------------------

    /**
     * Runs the admin interaction loop after verifying the admin PIN.
     *
     * @param inv active inventory instance
     */
    private static void runAdmin(Inventory inv) {
        String pin = prompt("Enter admin PIN");
        if (!ADMIN_PIN.equals(pin)) {
            println("Access denied.");
            return;
        }
        while (true) {
            println("""
                    
                    Admin Menu
                      1) View inventory
                      2) Restock product
                      3) Top sellers
                      0) Back
                    """);
            String choice = prompt("Select");
            switch (choice) {
                case "1" -> listItems(inv);
                case "2" -> doRestock(inv);
                case "3" -> showTopSellers(inv);
                case "0" -> { return; }
                default -> println("Invalid option.");
            }
        }
    }

    // -------------------- Actions --------------------

    /**
     * Prints a tabular list of all products in the inventory.
     *
     * @param inv active inventory instance
     */
    private static void listItems(Inventory inv) {
        println("\nID   Category     Name                         Price     Stock");
        println("---- ------------ ---------------------------- --------- -----");
        for (Product p : inv.all()) {
            printf("%-4d %-12s %-28s %-9s %5d%n",
                    p.getId(), p.getCategory(), p.getName(), formatMoney(p.getPrice()), p.getStock());
        }
    }

    /**
     * Prompts the user for a product ID and quantity, validates both, and adds the line to the cart.
     * Gracefully handles EOF and number format errors.
     *
     * @param inv   active inventory instance
     * @param cart  current customer's cart
     */
    private static void addToCart(Inventory inv, Cart cart) {
        try {
            Integer id = promptInt("Enter product ID");
            if (id == null) return; // user hit EOF or invalid entry already messaged

            Optional<Product> productOpt = inv.find(id);
            if (productOpt.isEmpty()) {
                println("Invalid product ID. Please enter a valid item number.");
                return;
            }

            Product p = productOpt.get();
            Integer qty = promptPositiveInt("Quantity");
            if (qty == null) return;

            if (qty > p.getStock()) {
                println("Not enough stock. Available: " + p.getStock());
                return;
            }

            cart.add(p, qty);
            println("Added " + qty + " × " + p.getName() + " to cart.");
        } catch (NoSuchElementException e) {
            println("Input closed. Returning to menu.");
        } catch (Exception e) {
            println("Error: " + e.getMessage());
        }
    }

    /**
     * Displays the cart contents and subtotal in a formatted table.
     *
     * @param cart current customer's cart
     */
    private static void viewCart(Cart cart) {
        if (cart.isEmpty()) {
            println("Your cart is empty.");
            return;
        }
        println("\nCart:");
        println("Qty  Item                           Price      Line Total");
        println("---  ----------------------------   --------   ----------");
        cart.lines().forEach((p, q) -> {
            double line = p.getPrice() * q;
            printf("%-4d %-28s   %-9s   %-10s%n",
                    q, p.getName(), formatMoney(p.getPrice()), formatMoney(line));
        });
        println("---------------------------------------------------------");
        println("Subtotal: " + formatMoney(cart.subtotal()));
    }

    /**
     * Validates stock, consumes inventory, clears the cart, and persists the inventory.
     *
     * @param inv  active inventory instance
     * @param cart current customer's cart
     */
    private static void checkout(Inventory inv, Cart cart) {
        if (cart.isEmpty()) {
            println("Cart is empty.");
            return;
        }
        // Double-check stock before consuming
        for (Map.Entry<Product, Integer> e : cart.lines().entrySet()) {
            if (e.getValue() > e.getKey().getStock()) {
                println("Insufficient stock for " + e.getKey().getName() + ". Please adjust cart.");
                return;
            }
        }
        cart.lines().forEach(Product::consume);
        double total = cart.subtotal();
        cart.clear();
        println("Checkout complete! Total: " + formatMoney(total));
        println("Thank you for your order.");
        // persist inventory after sale
        inv.saveToFile();
    }

    /**
     * Prompts for a product ID and quantity, updates stock, and persists the inventory.
     * Gracefully handles EOF and common exceptions.
     *
     * @param inv active inventory instance
     */
    private static void doRestock(Inventory inv) {
        try {
            Integer id = promptInt("Product ID to restock");
            if (id == null) return;

            Integer qty = promptPositiveInt("Add quantity");
            if (qty == null) return;

            inv.restock(id, qty);
            println("Restocked.");
            inv.saveToFile();
        } catch (NoSuchElementException e) {
            println("Input closed. Returning to menu.");
        } catch (Exception e) {
            println("Error: " + e.getMessage());
        }
    }

    /**
     * Displays the top-N selling products (default 5) with rank, sold count, stock, and price.
     *
     * @param inv active inventory instance
     */
    private static void showTopSellers(Inventory inv) {
        int n = 5;
        var list = inv.topSelling(n);
        if (list.isEmpty()) {
            println("No products.");
            return;
        }
        println("\nTop Sellers:");
        println("Rank Name                           Sold  Stock  Price");
        println("---- ----------------------------   ----  -----  --------");
        int rank = 1;
        for (Product p : list) {
            printf("%-4d %-28s   %-4d  %-5d  %-8s%n",
                    rank++, p.getName(), p.getSold(), p.getStock(), formatMoney(p.getPrice()));
        }
    }

    // -------------------- Seed Data --------------------

    /**
     * Initializes inventory and imports data from {@link #INVENTORY_FILE} if empty.
     *
     * @return a ready-to-use {@code Inventory} instance
     */
    private static Inventory seed() {
        Inventory inv = new Inventory();
        inv.importFromFileIfEmpty(INVENTORY_FILE); // seeds DB once if empty
        // load from text file if present
        return inv;
    }

    // -------------------- Helpers --------------------

    /**
     * Prompts the user for a line of input.
     * <p>
     * Returns an empty string on EOF and prints a small notice.
     *
     * @param msg message displayed before reading input
     * @return trimmed user input (never {@code null})
     */
    private static String prompt(String msg) {
        try {
            System.out.print(msg + ": ");
            String line = in.nextLine();
            return line == null ? "" : line.trim();
        } catch (NoSuchElementException e) { // EOF/Ctrl-D
            println("\n(no input)");
            return "";
        }
    }

    /**
     * Prompts for an integer, returning {@code null} on EOF or when parsing fails
     * (an explanatory message is printed on failure).
     *
     * @param msg prompt label
     * @return parsed integer, or {@code null} if unavailable/invalid
     */
    private static Integer promptInt(String msg) {
        String s = prompt(msg);
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            println("Enter a whole number.");
            return null;
        }
    }

    /**
     * Prompts for a positive integer (&gt; 0). Returns {@code null} on EOF or invalid input.
     *
     * @param msg prompt label
     * @return positive integer, or {@code null} if unavailable/invalid
     */
    private static Integer promptPositiveInt(String msg) {
        Integer v = promptInt(msg);
        if (v == null) return null;
        if (v <= 0) {
            println("Value must be greater than 0.");
            return null;
        }
        return v;
    }

    /**
     * Convenience wrapper around {@link System#out#println(String)} to keep output consistent.
     *
     * @param s text to print
     */
    private static void println(String s) {
        System.out.println(s);
    }

    /**
     * Convenience wrapper around {@link System#out#printf(String, Object...)}.
     *
     * @param fmt  format string
     * @param args format args
     */
    private static void printf(String fmt, Object... args) {
        System.out.printf(fmt, args);
    }

    /**
     * Formats a currency amount using the U.S. locale.
     *
     * @param amount a monetary value
     * @return formatted currency string (e.g., "$4.50")
     */
    private static String formatMoney(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}