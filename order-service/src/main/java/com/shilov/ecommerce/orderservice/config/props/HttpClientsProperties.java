package com.shilov.ecommerce.orderservice.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "app.http")
public class HttpClientsProperties {
    private Map<String, TimeoutSettings> clients = new HashMap<>();

    @Data
    public static class TimeoutSettings {
        private Duration connectTimeout;
        private Duration readTimeout;
    }
}
