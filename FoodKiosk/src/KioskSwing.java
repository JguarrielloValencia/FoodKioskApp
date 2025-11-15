import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


/**
 * <h1>Main GUI Program to find food kiosk and add them to cart!</h1>
 * Swing-based graphical user interface for the Food Kiosk application.
 * <p>
 * Features:
 * <ul>
 *   <li>Inventory browsing with search and category filtering</li>
 *   <li>Cart with quantity adjustments and line removal</li>
 *   <li>Checkout with stock validation against the latest database snapshot</li>
 *   <li>Admin login with restocking and viewing top sellers</li>
 *   <li>Optional dark mode theme</li>
 *   <li>CSV order logging to {@code orders.csv} for proof of transactions</li>
 * </ul>
 * <p>
 * This UI relies on {@link Inventory} (MySQL-backed) and {@link Cart} to manage
 * product data and cart contents.
 * @author Joseph Guarriello
 */

public class KioskSwing extends JFrame {
    /** Seed inventory file used if the database is empty on first run. */
    private static final String INVENTORY_FILE = "products.txt";
    /** CSV file used to log orders for proof/reporting purposes. */
    private static final String ORDERS_FILE = "orders.csv";
    /** Simple demo admin PIN. Do not use in production. */
    private static final String ADMIN_PIN = "1234";

    /** Currency formatter for U.S. dollars. */
    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);

    /** Inventory repository (MySQL-backed). */
    private final Inventory inventory = new Inventory();
    /** In-memory shopping cart for the current session. */
    private final Cart cart = new Cart();

    // --- Inventory UI ---
    private final InventoryTableModel inventoryModel = new InventoryTableModel();
    private final JTable inventoryTable = new JTable(inventoryModel);
    private final JTextField searchField = new JTextField(18);
    private final JComboBox<String> categoryFilter = new JComboBox<>(new String[] {
            "All", "Italian Drink", "Food", "Dessert", "Add-on", "Seasonal Special"
    });

    // --- Cart UI ---
    private final CartTableModel cartModel = new CartTableModel();
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel subtotalLabel = new JLabel("Subtotal: $0.00");
    private final JButton plusBtn = new JButton("+");
    private final JButton minusBtn = new JButton("–");
    private final JButton removeBtn = new JButton("Remove");

    // --- Controls ---
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final JButton addBtn = new JButton("Add to Cart");
    private final JButton checkoutBtn = new JButton("Checkout");

    // --- Admin session ---
    private boolean adminLoggedIn = false;
    private final JButton adminBtn = new JButton("Admin Login");
    private final JButton logoutBtn = new JButton("Logout");
    private final JButton topSellersBtn = new JButton("Top Sellers");
    private final JLabel adminStatus = new JLabel("User: Customer");

    // --- Appearance ---
    private final JCheckBox darkModeToggle = new JCheckBox("Dark mode");

    /**
     * Constructs the kiosk Swing UI, initializes the database-backed inventory,
     * wires all listeners, and builds the component layout.
     */
    public KioskSwing() {
        super("Food Kiosk (MySQL)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 620);
        setLocationRelativeTo(null);

        // One-time import from products.txt if DB is empty
        inventory.importFromFileIfEmpty(INVENTORY_FILE);

        // Load inventory from DB
        inventoryModel.setRows(inventory.all());
        inventoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inventoryTable.setFillsViewportHeight(true);
        inventoryTable.setRowHeight(26);
        alignInventoryColumns();

        // Top toolbar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topBar.add(new JLabel("Search:"));
        topBar.add(searchField);
        topBar.add(new JLabel("Category:"));
        topBar.add(categoryFilter);
        topBar.add(Box.createHorizontalStrut(20));
        darkModeToggle.addActionListener(e -> setDarkMode(darkModeToggle.isSelected()));
        topBar.add(darkModeToggle);

        // Inventory quick add
        JPanel invControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        invControls.add(new JLabel("Qty:"));
        invControls.add(qtySpinner);
        invControls.add(addBtn);

        // Cart panel
        cartTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cartTable.setFillsViewportHeight(true);
        cartTable.setRowHeight(26);
        alignCartColumns();

        JPanel cartButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        Dimension small = new Dimension(50, 28);
        plusBtn.setPreferredSize(small);
        minusBtn.setPreferredSize(small);
        removeBtn.setPreferredSize(new Dimension(90, 28));
        cartButtons.add(plusBtn);
        cartButtons.add(minusBtn);
        cartButtons.add(removeBtn);

        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.add(new JLabel("Cart"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JPanel cartBottom = new JPanel(new BorderLayout());
        JPanel subPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        subtotalLabel.setFont(subtotalLabel.getFont().deriveFont(Font.BOLD, 14f));
        subPanel.add(subtotalLabel);
        cartBottom.add(cartButtons, BorderLayout.WEST);
        cartBottom.add(subPanel, BorderLayout.EAST);
        rightPanel.add(cartBottom, BorderLayout.SOUTH);

        // Bottom controls
        logoutBtn.setVisible(false);
        topSellersBtn.setVisible(false);
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        bottomBar.add(checkoutBtn);
        bottomBar.add(Box.createHorizontalStrut(20));
        bottomBar.add(adminBtn);
        bottomBar.add(logoutBtn);
        bottomBar.add(topSellersBtn);
        bottomBar.add(Box.createHorizontalStrut(12));
        adminStatus.setFont(adminStatus.getFont().deriveFont(Font.BOLD));
        bottomBar.add(adminStatus);

        // Layout
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        leftPanel.add(topBar, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);
        leftPanel.add(invControls, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(leftPanel, BorderLayout.CENTER);
        root.add(rightPanel, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);
        setContentPane(root);

        // wiring
        wireFiltering();
        wireActions();

        refreshCartView();
        ensureOrdersHeader();
    }

    /**
     * Wires search field and category filter to reapply the inventory filter
     * whenever input changes or category selection is updated.
     */
    private void wireFiltering() {
        Runnable apply = () -> inventoryModel.applyFilter(
                searchField.getText().trim(),
                String.valueOf(categoryFilter.getSelectedItem())
        );
        searchField.getDocument().addDocumentListener(new SimpleDocListener(apply));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onAddToCart();
            }
        });
        categoryFilter.addActionListener(e -> apply.run());
    }

    /**
     * Wires button actions for add-to-cart, checkout, cart adjustments,
     * and admin/top seller functions.
     */
    private void wireActions() {
        addBtn.addActionListener(e -> onAddToCart());
        checkoutBtn.addActionListener(e -> onCheckout());

        plusBtn.addActionListener(e -> onAdjustCart(+1));
        minusBtn.addActionListener(e -> onAdjustCart(-1));
        removeBtn.addActionListener(e -> onRemoveLine());

        adminBtn.addActionListener(e -> onAdmin());
        logoutBtn.addActionListener(e -> doLogout());
        topSellersBtn.addActionListener(e -> showTopSellersDialog());
    }

    /**
     * Adds the currently selected inventory item to the cart using the quantity
     * from {@link #qtySpinner}, after validating stock and selection.
     */
    private void onAddToCart() {
        int row = inventoryTable.getSelectedRow();
        if (row < 0) { info("Select an item first."); return; }
        Product p = inventoryModel.getAt(row);
        int qty = ((Number) qtySpinner.getValue()).intValue();
        if (qty <= 0) { info("Quantity must be greater than 0."); return; }
        if (qty > p.getStock()) { info("Not enough stock. Available: " + p.getStock()); return; }
        cart.add(p, qty);
        refreshCartView();
    }

    /**
     * Adjusts the quantity of the selected cart line by the given delta.
     * If the new quantity is less than or equal to zero, the line is removed.
     *
     * @param delta change in quantity (e.g., +1 for plus, -1 for minus)
     */
    private void onAdjustCart(int delta) {
        int row = cartTable.getSelectedRow();
        if (row < 0) { info("Select a cart line first."); return; }
        CartLine cl = cartModel.getAt(row);
        int newQty = cl.qty + delta;
        if (newQty <= 0) cart.lines().remove(cl.product);
        else cart.lines().put(cl.product, newQty);
        refreshCartView();
    }

    /**
     * Removes the currently selected line item from the cart.
     */
    private void onRemoveLine() {
        int row = cartTable.getSelectedRow();
        if (row < 0) { info("Select a cart line first."); return; }
        CartLine cl = cartModel.getAt(row);
        cart.lines().remove(cl.product);
        refreshCartView();
    }

    /**
     * Performs the checkout operation:
     * <ul>
     *   <li>Revalidates stock against the latest database state</li>
     *   <li>Shows a confirmation dialog with cart summary and total</li>
     *   <li>Applies the sale to the database</li>
     *   <li>Logs the order to CSV</li>
     *   <li>Clears the cart and refreshes inventory view</li>
     * </ul>
     */
    private void onCheckout() {
        if (cart.isEmpty()) { info("Cart is empty."); return; }

        // Re-validate stock against latest DB snapshot
        Map<Integer, Product> latest = inventory.all().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        for (Map.Entry<Product,Integer> e : cart.lines().entrySet()) {
            Product live = latest.get(e.getKey().getId());
            if (live == null || e.getValue() > live.getStock()) {
                info("Insufficient stock for " + e.getKey().getName() + ".");
                return;
            }
        }

        StringBuilder sb = new StringBuilder("Items:\n");
        cart.lines().forEach((p, q) -> sb.append(q).append(" x ").append(p.getName())
                .append(" — ").append(money.format(p.getPrice() * q)).append("\n"));
        sb.append("\nTotal: ").append(money.format(cart.subtotal()));

        int res = JOptionPane.showConfirmDialog(this, sb.toString(),
                "Confirm Checkout", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        // snapshot, apply to DB, log CSV, refresh
        Map<Product,Integer> snapshot = new LinkedHashMap<>(cart.lines());
        inventory.applySale(snapshot);
        writeOrder(snapshot, cart.subtotal());

        cart.clear();
        inventoryModel.setRows(inventory.all());
        refreshCartView();
        info("Checkout complete! Total: " + money.format(
                snapshot.entrySet().stream().mapToDouble(e -> e.getKey().getPrice() * e.getValue()).sum()
        ));
    }

    /**
     * Handles admin login (PIN-based) and restocking logic.
     * <p>
     * If not logged in, prompts for PIN and, if valid, enables admin features.
     * If already logged in, treats the action as a restocking operation for
     * the currently selected inventory row.
     */
    private void onAdmin() {
        if (!adminLoggedIn) {
            String pin = JOptionPane.showInputDialog(this, "Enter admin PIN:");
            if (pin == null || !ADMIN_PIN.equals(pin.trim())) { info("Access denied."); return; }
            setAdminState(true);
            info("Admin logged in. Select a product then click 'Restock'.");
            return;
        }
        int row = inventoryTable.getSelectedRow();
        if (row < 0) { info("Select a product to restock."); return; }
        Product selected = inventoryModel.getAt(row);
        String qtyStr = JOptionPane.showInputDialog(this,
                "Add quantity for: " + selected.getName(), "10");
        if (qtyStr == null) return;
        try {
            int addQty = Integer.parseInt(qtyStr.trim());
            if (addQty <= 0) { info("Quantity must be > 0."); return; }
            inventory.restock(selected.getId(), addQty);
            inventoryModel.setRows(inventory.all());
            info("Restocked " + addQty + " units of " + selected.getName() + ".");
        } catch (NumberFormatException nfe) {
            info("Enter a whole number.");
        } catch (Exception ex) {
            info("Error: " + ex.getMessage());
        }
    }

    /**
     * Logs out the admin and returns the UI to customer mode.
     */
    private void doLogout() {
        setAdminState(false);
        info("Admin logged out.");
    }

    /**
     * Updates UI state based on whether an admin is logged in.
     *
     * @param loggedIn {@code true} if admin is logged in; {@code false} otherwise
     */
    private void setAdminState(boolean loggedIn) {
        this.adminLoggedIn = loggedIn;
        adminBtn.setText(loggedIn ? "Restock" : "Admin Login");
        logoutBtn.setVisible(loggedIn);
        topSellersBtn.setVisible(loggedIn);
        adminStatus.setText(loggedIn ? "User: ADMIN" : "User: Customer");
    }

    /**
     * Displays the top 5 selling products in a modal dialog with a small table view.
     */
    private void showTopSellersDialog() {
        List<Product> top = inventory.topSelling(5);
        if (top.isEmpty()) { info("No products."); return; }
        String[] cols = {"Rank", "Name", "Sold", "Stock", "Price"};
        String[][] data = new String[top.size()][cols.length];
        int i = 0; int rank = 1;
        for (Product p : top) {
            data[i++] = new String[] {
                    String.valueOf(rank++),
                    p.getName(),
                    String.valueOf(p.getSold()),
                    String.valueOf(p.getStock()),
                    money.format(p.getPrice())
            };
        }
        JTable table = new JTable(data, cols);
        table.setEnabled(false);
        table.setRowHeight(24);
        rightAlignColumn(table, 2);
        rightAlignColumn(table, 3);
        rightAlignColumn(table, 4);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(520, 180));
        JOptionPane.showMessageDialog(this, sp, "Top Sellers", JOptionPane.PLAIN_MESSAGE);
    }

    // --- CSV order log (for proof) ---

    /**
     * Ensures the orders CSV file exists and has a header row. If the file
     * does not exist, it is created with a CSV header.
     */
    private void ensureOrdersHeader() {
        try {
            if (!Files.exists(Paths.get(ORDERS_FILE))) {
                try (FileWriter fw = new FileWriter(ORDERS_FILE, true)) {
                    fw.write("timestamp,product,qty,unit_price,line_total,order_total\n");
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Appends order lines to the CSV log for a completed order.
     *
     * @param lines      map of products to quantities purchased
     * @param orderTotal total cost of the entire order
     */
    private void writeOrder(Map<Product,Integer> lines, double orderTotal) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String ts = LocalDateTime.now().format(fmt);
        try (FileWriter fw = new FileWriter(ORDERS_FILE, true)) {
            for (Map.Entry<Product,Integer> e : lines.entrySet()) {
                Product p = e.getKey();
                int q = e.getValue();
                double line = p.getPrice() * q;
                fw.write(String.format("%s,%s,%d,%.2f,%.2f,%.2f%n",
                        ts, sanitize(p.getName()), q, p.getPrice(), line, orderTotal));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Sanitizes product names to be CSV-safe by removing commas.
     *
     * @param s original product name
     * @return name with commas replaced by spaces
     */
    private String sanitize(String s) { return s.replace(",", " "); }

    // --- UI helpers ---

    /**
     * Refreshes the cart table view and subtotal label based on the current
     * contents of {@link #cart}.
     */
    private void refreshCartView() {
        cartModel.setLines(cart.lines());
        subtotalLabel.setText("Subtotal: " + money.format(cart.subtotal()));
    }

    /**
     * Displays an informational message dialog.
     *
     * @param msg message to display
     */
    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Toggles between light and dark mode by adjusting component tree colors
     * and table grid colors.
     *
     * @param on {@code true} to enable dark mode; {@code false} for default look
     */
    private void setDarkMode(boolean on) {
        Color bg = on ? new Color(0x121212) : UIManager.getColor("Panel.background");
        Color fg = on ? new Color(0xEAEAEA) : UIManager.getColor("Label.foreground");
        setComponentTreeColors(getContentPane(), bg, fg);
        inventoryTable.setGridColor(on ? new Color(0x2a2a2a) : Color.LIGHT_GRAY);
        cartTable.setGridColor(on ? new Color(0x2a2a2a) : Color.LIGHT_GRAY);
        repaint();
    }

    /**
     * Recursively sets background and foreground colors on the given component
     * and its children.
     *
     * @param c  root component
     * @param bg background color
     * @param fg foreground color
     */
    private void setComponentTreeColors(Component c, Color bg, Color fg) {
        if (c instanceof JComponent jc) {
            jc.setOpaque(true);
            jc.setBackground(bg);
            jc.setForeground(fg);
            if (jc instanceof JScrollPane sp) {
                sp.getViewport().setBackground(bg);
                sp.getViewport().setForeground(fg);
            }
            if (jc instanceof JTable t) {
                t.getTableHeader().setOpaque(true);
                t.getTableHeader().setBackground(bg.darker());
                t.getTableHeader().setForeground(fg);
            }
        }
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) setComponentTreeColors(child, bg, fg);
        }
    }

    /**
     * Aligns numeric inventory columns (ID, price, stock) to the right for better readability.
     */
    private void alignInventoryColumns() {
        rightAlignColumn(inventoryTable, 0); // ID
        rightAlignColumn(inventoryTable, 3); // Price
        rightAlignColumn(inventoryTable, 4); // Stock
    }

    /**
     * Aligns numeric cart columns (qty, price, line total) to the right.
     */
    private void alignCartColumns() {
        rightAlignColumn(cartTable, 1); // Qty
        rightAlignColumn(cartTable, 2); // Price
        rightAlignColumn(cartTable, 3); // Line Total
    }

    /**
     * Sets a table column's cell renderer to right-align values.
     *
     * @param table table whose column should be aligned
     * @param col   column index to right-align
     */
    private static void rightAlignColumn(JTable table, int col) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(col).setCellRenderer(r);
    }

    /**
     * Entry point for launching the Swing kiosk UI.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KioskSwing().setVisible(true));
    }

    // ---------- Table models ----------

    /**
     * Simple data holder representing a line item in the cart table.
     */
    private static class CartLine {
        final Product product;
        final int qty;
        final double price;

        CartLine(Product p, int q) { this.product = p; this.qty = q; this.price = p.getPrice(); }

        double lineTotal() { return price * qty; }
    }

    /**
     * Table model backing the cart table, showing item, quantity, price, and line total.
     */
    private class CartTableModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Qty", "Price", "Line Total"};
        private List<CartLine> rows = new ArrayList<>();

        /**
         * Rebuilds the internal list of cart lines from the given product-to-quantity map.
         *
         * @param map map of products to quantities
         */
        void setLines(Map<Product,Integer> map) {
            rows = map.entrySet().stream().map(e -> new CartLine(e.getKey(), e.getValue())).collect(Collectors.toList());
            fireTableDataChanged();
        }

        CartLine getAt(int r) { return rows.get(r); }

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return cols.length; }

        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            CartLine cl = rows.get(r);
            return switch (c) {
                case 0 -> cl.product.getName();
                case 1 -> cl.qty;
                case 2 -> money.format(cl.price);
                case 3 -> money.format(cl.lineTotal());
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int c) { return (c == 1) ? Integer.class : String.class; }

        @Override public boolean isCellEditable(int r, int c) { return c == 1; }

        /**
         * Allows editing of the quantity column; updates the underlying cart map accordingly.
         */
        @Override public void setValueAt(Object aValue, int r, int c) {
            if (c != 1) return;
            try {
                int newQty = Integer.parseInt(aValue.toString().trim());
                if (newQty <= 0) cart.lines().remove(rows.get(r).product);
                else cart.lines().put(rows.get(r).product, newQty);
                refreshCartView();
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Table model for displaying inventory rows with optional search and category filtering.
     */
    private class InventoryTableModel extends AbstractTableModel {
        private final String[] cols = {"ID", "Category", "Name", "Price", "Stock"};
        private List<Product> filtered = new ArrayList<>();

        /**
         * Initializes the table model with the full product list,
         * then applies a default filter (no search, "All" category).
         *
         * @param products product list to display
         */
        void setRows(List<Product> products) { this.filtered = new ArrayList<>(products); applyFilter("", "All"); }

        /**
         * Applies text and category filters to rebuild the visible inventory list.
         *
         * @param query    search text (matches name or category, case-insensitive)
         * @param category category filter, or {@code "All"} to show all
         */
        void applyFilter(String query, String category) {
            String q = query.toLowerCase(Locale.ROOT);
            filtered = inventory.all().stream()
                    .filter(p -> "All".equals(category) || p.getCategory().equalsIgnoreCase(category))
                    .filter(p -> q.isEmpty()
                            || p.getName().toLowerCase(Locale.ROOT).contains(q)
                            || p.getCategory().toLowerCase(Locale.ROOT).contains(q))
                    .sorted(Comparator.comparing(Product::getId))
                    .collect(Collectors.toList());
            fireTableDataChanged();
        }

        Product getAt(int r) { return filtered.get(r); }

        @Override public int getRowCount() { return filtered.size(); }

        @Override public int getColumnCount() { return cols.length; }

        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            Product p = filtered.get(r);
            return switch (c) {
                case 0 -> p.getId();
                case 1 -> p.getCategory();
                case 2 -> p.getName();
                case 3 -> money.format(p.getPrice());
                case 4 -> p.getStock();
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int c) { return (c == 0 || c == 4) ? Integer.class : String.class; }
    }

    /**
     * Tiny document-listener helper that calls a {@link Runnable} on any text change.
     */
    private record SimpleDocListener(Runnable r) implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e) { r.run(); }
        @Override public void removeUpdate(DocumentEvent e) { r.run(); }
        @Override public void changedUpdate(DocumentEvent e) { r.run(); }
    }
}
