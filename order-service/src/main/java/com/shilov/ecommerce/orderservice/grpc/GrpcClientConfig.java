package com.shilov.ecommerce.orderservice.grpc;

import com.shilov.ecommerce.grpc.product.v1.ProductServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    static final String PRODUCT_SERVICE_CHANNEL = "product-service";

    @Bean
    ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub(GrpcChannelFactory channelFactory) {
        return ProductServiceGrpc.newBlockingStub(channelFactory.createChannel(PRODUCT_SERVICE_CHANNEL));
    }

    @Bean
    GrpcChannelBuilderCustomizer<?> productServiceRetryCustomer() {
        return GrpcChannelBuilderCustomizer.matching(PRODUCT_SERVICE_CHANNEL, builder -> builder.enableRetry());
    }

}
