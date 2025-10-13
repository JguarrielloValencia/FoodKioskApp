import java.util.Objects;

public class Product {
    private final int id;
    private final String name;
    private final String category;
    private final double price;
    private int stock;
    private int sold;

    public Product(int id, String name, String category, double price, int stock) {
        if (price < 0 || stock < 0) throw new IllegalArgumentException("Price/stock cannot be negative");
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.sold = 0;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public int getSold() { return sold; }

    public void restock(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Restock must be positive");
        stock += qty;
    }

    public void consume(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (qty > stock) throw new IllegalStateException("Not enough stock");
        stock -= qty;
        sold += qty;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product p)) return false;
        return id == p.id;
    }
    @Override public int hashCode() { return Objects.hash(id); }
}