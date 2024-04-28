package net.greeta.order.config;

import net.greeta.order.config.auth.UserCredentialsDefaultProvider;
import net.greeta.order.config.auth.UserCredentialsProvider;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockBeanConfig {

    @Bean
    @Primary
    public UserCredentialsProvider userCredentialsProvider(UserCredentialsDefaultProvider userCredentialsDefaultProvider) {
        return Mockito.spy(UserCredentialsDefaultProvider.class);
    }
}
