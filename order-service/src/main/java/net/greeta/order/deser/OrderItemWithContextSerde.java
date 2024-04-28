package net.greeta.order.deser;

import net.greeta.order.models.OrderItemWithContext;
import org.apache.kafka.common.serialization.Serdes;

public class OrderItemWithContextSerde extends Serdes.WrapperSerde<OrderItemWithContext> {
    public OrderItemWithContextSerde() {
        super(new JsonSerializer<>(), new JsonDeserializer<>(OrderItemWithContext.class));
    }
}
