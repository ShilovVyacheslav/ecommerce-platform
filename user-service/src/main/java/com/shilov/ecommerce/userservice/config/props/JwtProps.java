package com.shilov.ecommerce.userservice.config.props;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.security.jwt")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtProps {
    private RSAPrivateKey privateKeyLocation;
    private RSAPublicKey publicKeyLocation;
    private long accessExpiration;
    private long refreshExpiration;
}
