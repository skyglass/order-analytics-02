package net.greeta.stock.analytics.services;

import net.greeta.order.common.domain.dto.analytics.Order;

public interface OrdersService {
  Order getSubmittedOrder(String orderId);

  Order getPaidOrder(String orderId);
}
