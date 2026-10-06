package com.shilov.ecommerce.orderservice.config;

import lombok.Data;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Configuration
@ConfigurationProperties(prefix = "app.http.clients")
public class RestClientConfig {

    private static final String PAYMENT_SERVICE = "payment-service";

    private Map<String, TimeoutSettings> timeoutSettings = new HashMap<>();

    @Data
    public static class TimeoutSettings {
        private Duration connectTimeout;
        private Duration readTimeout;
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient paymentServiceRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeoutSettings.get(PAYMENT_SERVICE).getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeoutSettings.get(PAYMENT_SERVICE).getReadTimeout());
        return loadBalancedRestClientBuilder.clone()
                .baseUrl("http://payment-service")
                .requestFactory(requestFactory)
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
