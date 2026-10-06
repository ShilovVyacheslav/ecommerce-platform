package com.shilov.ecommerce.orderservice.config;

import com.shilov.ecommerce.orderservice.config.props.HttpClientsProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(HttpClientsProperties.class)
public class RestClientConfig {

    private static final String PAYMENT_SERVICE = "payment-service";

    private final HttpClientsProperties httpClientsProperties;

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
        HttpClientsProperties.TimeoutSettings settings =
                httpClientsProperties.getClients().get(PAYMENT_SERVICE);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.getReadTimeout());
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
