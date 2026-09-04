package com.shilov.ecommerce.orderservice.config;

import org.slf4j.MDC;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder
                .baseUrl("http://product-service")
                .requestInterceptor(correlationIdPropagatingInterceptor())
                .build();
    }

    @Bean
    public RestClient paymentServiceRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder
                .baseUrl("http://payment-service")
                .requestInterceptor(correlationIdPropagatingInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor correlationIdPropagatingInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }

}
