package net.greeta.order.common.domain.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BasketItem {
  private UUID productId;
  private String productName;
  private Integer quantity;
}
