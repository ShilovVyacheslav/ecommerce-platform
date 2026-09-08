package com.shilov.ecommerce.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.internal")
public class InternalApiProperties {
    private String apiKey;
}
