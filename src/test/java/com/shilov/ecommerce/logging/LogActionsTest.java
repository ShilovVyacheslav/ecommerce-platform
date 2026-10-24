package com.shilov.ecommerce.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogActions")
public class LogActionsTest {

    @Test
    @DisplayName("Every channel -> <channel>:<operation>")
    void actions_AllChannels_UseChannelPrefix() {
        assertThat(LogActions.http(LogActionsTest.class, "createOrder")).isEqualTo("http:LogActionsTest.createOrder");
        assertThat(LogActions.kafka("order-events")).isEqualTo("kafka:order-events");
        assertThat(LogActions.job(LogActionsTest.class, "publish")).isEqualTo("job:LogActionsTest.publish");
        assertThat(LogActions.gateway("order-service")).isEqualTo("gateway:order-service");
    }

    @Test
    @DisplayName("gRPC full method name -> package dropped, service and method kept")
    void grpc_FullMethodName_DropsPackage() {
        assertThat(LogActions.grpc("ecommerce.product.v1.ProductService/ReserveStock"))
                .isEqualTo("grpc:ProductService/ReserveStock");
        assertThat(LogActions.grpc("ProductService/ReserveStock")).isEqualTo("grpc:ProductService/ReserveStock");
    }

}
