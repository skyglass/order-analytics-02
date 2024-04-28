package net.greeta.order.common.domain.dto.inventory;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddStockRequest(UUID productId, Integer quantity) {
}
