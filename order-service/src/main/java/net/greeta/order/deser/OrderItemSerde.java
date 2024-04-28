package net.greeta.order.deser;

import net.greeta.order.models.OrderItem;
import org.apache.kafka.common.serialization.Serdes;

public class OrderItemSerde extends Serdes.WrapperSerde<OrderItem> {
    public OrderItemSerde() {
        super(new JsonSerializer<>(), new JsonDeserializer<>(OrderItem.class));
    }
}
