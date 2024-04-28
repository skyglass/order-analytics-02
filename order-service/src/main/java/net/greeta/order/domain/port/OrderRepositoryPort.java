package net.greeta.order.domain.port;

import net.greeta.order.common.domain.dto.order.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

  Optional<Order> findOrderById(UUID orderId);

  void saveOrder(Order order);
}
