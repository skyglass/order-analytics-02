package net.greeta.order.domain.port;

import net.greeta.order.common.domain.dto.order.OrderRequest;
import net.greeta.order.common.domain.dto.order.Order;

import java.util.UUID;

public interface OrderUseCasePort {

  UUID placeOrder(OrderRequest orderRequest);

  void updateOrderStatus(UUID orderId, boolean success);

  Order getOrder(UUID orderId);

}
