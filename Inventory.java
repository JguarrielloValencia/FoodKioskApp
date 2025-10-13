
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Inventory {
    private final Map<Integer, Product> byId = new ConcurrentHashMap<>();

    public void add(Product p) {
        if (byId.containsKey(p.getId())) throw new IllegalArgumentException("Duplicate product id: " + p.getId());
        byId.put(p.getId(), p);
    }

    public Optional<Product> find(int id) { return Optional.ofNullable(byId.get(id)); }

    public List<Product> all() {
        return byId.values().stream()
                .sorted(Comparator.comparing(Product::getCategory).thenComparing(Product::getName))
                .collect(Collectors.toList());
    }

    public void restock(int productId, int qty) {
        Product p = byId.get(productId);
        if (p == null) throw new NoSuchElementException("Product not found: " + productId);
        p.restock(qty);
    }

    public List<Product> topSelling(int n) {
        return byId.values().stream()
                .sorted(Comparator.comparing(Product::getSold).reversed())
                .limit(Math.max(n, 0))
                .collect(Collectors.toList());
    }

    // ---------- NEW: Load and Save ----------
    public void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("Warning: file not found: " + filename);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 5) continue;
                int id = Integer.parseInt(parts[0].trim());
                String category = parts[1].trim();
                String name = parts[2].trim();
                double price = Double.parseDouble(parts[3].trim());
                int stock = Integer.parseInt(parts[4].trim());
                add(new Product(id, name, category, price, stock));
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    public void saveToFile(String filename) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            for (Product p : all()) {
                out.printf("%d,%s,%s,%.2f,%d%n",
                        p.getId(), p.getCategory(), p.getName(), p.getPrice(), p.getStock());
            }
        } catch (IOException e) {
            System.out.println("Error writing file: " + e.getMessage());
        }
    }
}