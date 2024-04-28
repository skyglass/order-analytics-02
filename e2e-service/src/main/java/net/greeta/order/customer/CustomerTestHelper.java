package net.greeta.order.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.greeta.order.common.domain.dto.customer.Customer;
import net.greeta.order.common.domain.dto.customer.CustomerRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerTestHelper {

    private final CustomerClient customerClient;

    public Customer createCustomer(String username, double balance) {
        CustomerRequest customer = CustomerRequest.builder()
                .username(username)
                .fullName(username)
                .balance(BigDecimal.valueOf(balance))
                .build();
        return customerClient.create(customer);
    }



}

