package net.greeta.order.common.domain.dto.order;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

  private UUID id;

  private int version;

  private UUID customerId;

  private UUID productId;

  private BigDecimal price;

  private int quantity;

  private Timestamp createdAt;

  private OrderStatus status;
}
