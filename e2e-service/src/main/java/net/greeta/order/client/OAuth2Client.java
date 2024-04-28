package net.greeta.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "oauth2")
public interface OAuth2Client {

    @PostMapping(value = "/realms/stock-realm/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Oauth2TokenDto getToken(@RequestBody Map<String, ?> form);
}
