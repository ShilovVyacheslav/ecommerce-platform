package com.shilov.ecommerce.productservice.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

@Configuration(proxyBeanMethods = false)
public class DisableGrpcSpringSecurityConfig {

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor authenticationProcessInterceptor(GrpcSecurity grpcSecurity) throws Exception {
        grpcSecurity.authorizeRequests(requests -> requests.allRequests().permitAll());
        return grpcSecurity.build();
    }

}
