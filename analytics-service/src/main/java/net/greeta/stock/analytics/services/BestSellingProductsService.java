package net.greeta.stock.analytics.services;

import net.greeta.order.common.domain.dto.analytics.Product;

import java.util.List;

public interface BestSellingProductsService {
  List<Product> topFiveProducts();
}
