import java.text.NumberFormat;
import java.util.*;

public class App {
    private static final Scanner in = new Scanner(System.in);
    private static final String ADMIN_PIN = "1234"; // demo only
    private static final String INVENTORY_FILE = "products.txt";

    public static void main(String[] args) {
        Inventory inventory = seed();

        println("\n=== Food-Type Kiosk (Console) ===");
        while (true) {
            println("""
                    
                    Choose mode:
                      1) Customer
                      2) Admin
                      0) Exit
                    """);
            switch (prompt("Select")) {
                case "1" -> runCustomer(inventory);
                case "2" -> runAdmin(inventory);
                case "0" -> { println("Bye!"); return; }
                default -> println("Invalid option.");
            }
        }
    }

    // -------------------- Customer --------------------
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
    private static void listItems(Inventory inv) {
        println("\nID   Category     Name                         Price     Stock");
        println("---- ------------ ---------------------------- --------- -----");
        for (Product p : inv.all()) {
            printf("%-4d %-12s %-28s %-9s %5d%n",
                    p.getId(), p.getCategory(), p.getName(), formatMoney(p.getPrice()), p.getStock());
        }
    }

    private static void addToCart(Inventory inv, Cart cart) {
        try {
            int id = Integer.parseInt(prompt("Enter product ID"));
            Optional<Product> productOpt = inv.find(id);

            if (productOpt.isEmpty()) {
                println("Invalid product ID. Please enter a valid item number.");
                return;
            }

            Product p = productOpt.get();
            int qty = Integer.parseInt(prompt("Quantity"));

            if (qty <= 0) {
                println("Quantity must be greater than 0.");
                return;
            }

            if (qty > p.getStock()) {
                println("Not enough stock. Available: " + p.getStock());
                return;
            }

            cart.add(p, qty);
            println("Added " + qty + " x " + p.getName() + " to cart.");
        } catch (NumberFormatException e) {
            println("Enter valid numbers only.");
        } catch (Exception e) {
            println("Error: " + e.getMessage());
        }
    }

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

    private static void checkout(Inventory inv, Cart cart) {
        if (cart.isEmpty()) {
            println("Cart is empty.");
            return;
        }
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
        inv.saveToFile(INVENTORY_FILE);
    }

    private static void doRestock(Inventory inv) {
        try {
            int id = Integer.parseInt(prompt("Product ID to restock"));
            int qty = Integer.parseInt(prompt("Add quantity"));
            inv.restock(id, qty);
            println("Restocked.");

            // persist inventory after restock
            inv.saveToFile(INVENTORY_FILE);
        } catch (Exception e) {
            println("Error: " + e.getMessage());
        }
    }

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
    private static Inventory seed() {
        Inventory inv = new Inventory();
        inv.loadFromFile(INVENTORY_FILE); // load from text file if present
        return inv;
    }

    // -------------------- Helpers --------------------
    private static String prompt(String msg) {
        System.out.print(msg + ": ");
        return in.nextLine().trim();
    }

    private static void println(String s) {
        System.out.println(s);
    }

    private static void printf(String fmt, Object... args) {
        System.out.printf(fmt, args);
    }

    private static String formatMoney(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}