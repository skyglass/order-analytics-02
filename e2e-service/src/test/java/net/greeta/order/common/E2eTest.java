package net.greeta.order.common;

import lombok.SneakyThrows;
import net.greeta.order.client.KafkaClient;
import net.greeta.order.config.MockHelper;
import net.greeta.order.customer.CustomerTestDataService;
import net.greeta.order.inventory.InventoryTestDataService;
import net.greeta.order.OrderTestDataService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class E2eTest {

    @Value("${security.oauth2.username}")
    private String securityOauth2Username;

    @Value("${security.oauth2.password}")
    private String securityOauth2Password;

    @Autowired
    private MockHelper mockHelper;

    @Autowired
    private CustomerTestDataService customerTestDataService;

    @Autowired
    private InventoryTestDataService inventoryTestDataService;

    @Autowired
    private OrderTestDataService orderTestDataService;

    @Autowired
    private KafkaClient kafkaClient;

    @BeforeEach
    @SneakyThrows
    void cleanup() {
        kafkaClient.clearMessages("CUSTOMER.events");
        kafkaClient.clearMessages("PRODUCT.events");
        kafkaClient.clearMessages("ORDER.events");
        kafkaClient.clearMessages("ORDER.events.dlq");
        mockHelper.mockCredentials(securityOauth2Username, securityOauth2Password);
        orderTestDataService.resetDatabase();
        inventoryTestDataService.resetDatabase();
        customerTestDataService.resetDatabase();
        //TimeUnit.MILLISECONDS.sleep(Duration.ofSeconds(1).toMillis());
    }
}
