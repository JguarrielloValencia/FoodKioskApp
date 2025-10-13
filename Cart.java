import java.util.LinkedHashMap;
import java.util.Map;

public class Cart {
    private final Map<Product, Integer> lines = new LinkedHashMap<>();

    public void add(Product p, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        lines.merge(p, qty, Integer::sum);
    }

    public double subtotal() {
        return lines.entrySet().stream()
                .mapToDouble(e -> e.getKey().getPrice() * e.getValue())
                .sum();
    }

    public Map<Product, Integer> lines() { return lines; }

    public boolean isEmpty() { return lines.isEmpty(); }

    public void clear() { lines.clear(); }
}
