import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String id,
        String customerId,
        BigDecimal amount,
        OrderStatus status,
        Instant createdAt) {}
