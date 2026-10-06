import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Intentionally undocumented -- Session 5B asks students to add
// JavaDoc describing the contract of each public method.
public class OrderBook {

    private final Map<String, Order> store = new ConcurrentHashMap<>();

    public List<Order> list() {
        return List.copyOf(store.values());
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Creates a new order with status NEW and a generated id.
     *
     * @param customerId non-blank customer identifier; leading/trailing
     *                   whitespace is ignored
     * @param amount     order amount, must be greater than zero and at
     *                   most 1000000
     * @return the newly created, persisted Order
     * @throws IllegalArgumentException if customerId is null or blank,
     *         amount is null, not positive, or exceeds the maximum
     */
    public Order create(String customerId, BigDecimal amount) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId required");
        }
        String normalizedId = customerId.trim();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("amount exceeds maximum 1000000");
        }
        Order o = new Order(UUID.randomUUID().toString(),
                            normalizedId, amount,
                            OrderStatus.NEW, Instant.now());
        store.put(o.id(), o);
        return o;
    }

    public boolean cancel(String id) {
        Order existing = store.get(id);
        if (existing == null || existing.status() == OrderStatus.CANCELLED) {
            return false;
        }
        store.put(id, new Order(existing.id(), existing.customerId(),
                                existing.amount(), OrderStatus.CANCELLED,
                                existing.createdAt()));
        return true;
    }

    public BigDecimal totalFor(String customerId) {
        return store.values().stream()
                .filter(o -> customerId.equals(o.customerId())
                          && o.status() == OrderStatus.NEW)
                .map(Order::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
