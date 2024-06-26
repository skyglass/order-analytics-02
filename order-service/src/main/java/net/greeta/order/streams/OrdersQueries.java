package net.greeta.order.streams;

import net.greeta.order.models.TimePeriod;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.greeta.order.models.OrdersSummary;
import net.greeta.order.models.KStreamsWindowStore;

import java.time.Instant;

@Component
public class OrdersQueries {

    @Autowired
    KafkaStreams streams;

    public OrdersSummary ordersSummary() {
        KStreamsWindowStore<Long> countStore = new KStreamsWindowStore<>(ordersCountsStore());
        KStreamsWindowStore<Double> revenueStore = new KStreamsWindowStore<>(revenueStore());

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        Instant twoMinutesAgo = now.minusSeconds(120);

        long recentCount = countStore.firstEntry(oneMinuteAgo, now);
        double recentRevenue = revenueStore.firstEntry(oneMinuteAgo, now);

        long  previousCount = countStore.firstEntry(twoMinutesAgo, oneMinuteAgo);
        double previousRevenue = revenueStore.firstEntry(twoMinutesAgo, oneMinuteAgo);

        TimePeriod currentTimePeriod = new TimePeriod(recentCount, recentRevenue);
        TimePeriod previousTimePeriod = new TimePeriod(previousCount, previousRevenue);
        return new OrdersSummary(
                currentTimePeriod, previousTimePeriod
        );
    }

    private ReadOnlyWindowStore<String, Double> revenueStore() {
        while (true) {
            try {
                return streams.store(StoreQueryParameters.fromNameAndType(
                        "RevenueStore", QueryableStoreTypes.windowStore())
                );
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
            }
        }
    }

    private ReadOnlyWindowStore<String, Long> ordersCountsStore() {
        while (true) {
            try {
                return streams.store(StoreQueryParameters.fromNameAndType(
                        "OrdersCountStore", QueryableStoreTypes.windowStore())
                );
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
            }
        }
    }
}