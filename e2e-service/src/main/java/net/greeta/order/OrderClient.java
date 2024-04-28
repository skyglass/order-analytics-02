package net.greeta.order;

import jakarta.validation.Valid;
import net.greeta.order.common.domain.dto.order.Order;
import net.greeta.order.common.domain.dto.order.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "order")
public interface OrderClient {

    @PostMapping("/")
    UUID placeOrder(@RequestBody @Valid OrderRequest orderRequest);

    @GetMapping("/{orderId}")
    Order getOrder(@PathVariable UUID orderId);

}
