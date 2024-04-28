package net.greeta.order.api;

import net.greeta.order.domain.port.OrderUseCasePort;
import net.greeta.order.common.domain.dto.order.OrderRequest;
import net.greeta.order.common.domain.dto.order.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OrderController {

  private final OrderUseCasePort orderUseCase;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UUID placeOrder(@RequestBody @Valid OrderRequest orderRequest) {
    log.info("Received new order request {}", orderRequest);
    return orderUseCase.placeOrder(orderRequest);
  }

  @GetMapping("{orderId}")
  public Order getOrder(@PathVariable UUID orderId) {
    return orderUseCase.getOrder(orderId);
  }
}
