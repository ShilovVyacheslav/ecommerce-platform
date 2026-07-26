package com.shilov.ecommerce.userservice.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.shilov.ecommerce.userservice.config.props.JwtProps;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JwkConfig {

    private final JwtProps jwtProps;

    @Bean
    public JWKSet jwkSet() {
        RSAKey rsaKey = new RSAKey.Builder(jwtProps.getPublicKeyLocation())
                .keyID("user-service-key-1")
                .build();
        return new JWKSet(rsaKey);
    }
}
