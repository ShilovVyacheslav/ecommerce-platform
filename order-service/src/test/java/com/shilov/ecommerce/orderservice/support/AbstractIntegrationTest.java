package com.shilov.ecommerce.orderservice.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.shilov.ecommerce.orderservice.dto.OrderCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.support.grpc.FakeProductService;
import com.shilov.ecommerce.orderservice.support.wiremock.PaymentServiceCallRecorder;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole order-service against real infrastructure and protocol-level fakes of its neighbours:
 * <ul>
 *     <li>PostgreSQL (Testcontainers) with the real Flyway migrations and {@code ddl-auto: validate};</li>
 *     <li>product-service: {@link FakeProductService} behind a real gRPC (Netty) server on a random port;</li>
 *     <li>payment-service: WireMock, reached through the {@code @LoadBalanced} RestClient
 *     via the simple discovery client.</li>
 * </ul>
 * Infrastructure is started once per JVM and shared by every Spring context; state is wiped before each test.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    protected static final String INTERNAL_API_KEY = "integration-test-key";

    private static final String PRODUCT_SERVICE_NAME = "ecommerce.product.v1.ProductService";

    protected static final SagaCallJournal JOURNAL = new SagaCallJournal();
    protected static final FakeProductService PRODUCT_SERVICE = new FakeProductService(JOURNAL);
    protected static final WireMockServer PAYMENT_SERVICE =
            new WireMockServer(options().dynamicPort().extensions(new PaymentServiceCallRecorder(JOURNAL)));

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Server PRODUCT_SERVICE_SERVER = startProductService();

    static {
        POSTGRES.start();
        PAYMENT_SERVICE.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.grpc.client.channels.product-service.address",
                () -> "static://localhost:" + PRODUCT_SERVICE_SERVER.getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.payment-service[0].uri",
                PAYMENT_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, order_items, orders");
        PAYMENT_SERVICE.resetAll();
        PRODUCT_SERVICE.reset();
        JOURNAL.clear();
        JOURNAL.usePersistedStatusProbe(this::persistedStatus);
    }

    protected OrderResponseDto placeOrder() throws Exception {
        return placeOrder(UnaryOperator.identity());
    }

    protected OrderResponseDto placeOrder(UnaryOperator<MockHttpServletRequestBuilder> customizer) throws Exception {
        String body = performPlaceOrder(TestOrders.createRequest(), customizer)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, OrderResponseDto.class);
    }

    protected ResultActions performPlaceOrder(OrderCreateDto orderCreateDto) throws Exception {
        return performPlaceOrder(orderCreateDto, UnaryOperator.identity());
    }

    protected ResultActions performPlaceOrder(OrderCreateDto orderCreateDto,
                                              UnaryOperator<MockHttpServletRequestBuilder> customizer)
            throws Exception {
        return mockMvc.perform(customizer.apply(post("/api/v1/orders")
                .with(jwt().jwt(token -> token.claim("id", TestOrders.USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderCreateDto))));
    }

    protected OrderStatus persistedStatus(UUID orderId) {
        return jdbcTemplate.query("SELECT status FROM orders WHERE id = ?",
                resultSet -> resultSet.next() ? OrderStatus.valueOf(resultSet.getString(1)) : null,
                orderId);
    }

    private static Server startProductService() {
        HealthStatusManager health = new HealthStatusManager();
        health.setStatus("", ServingStatus.SERVING);
        health.setStatus(PRODUCT_SERVICE_NAME, ServingStatus.SERVING);
        try {
            Server server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                    .addService(ServerInterceptors.intercept(PRODUCT_SERVICE, PRODUCT_SERVICE.metadataRecorder()))
                    .addService(health.getHealthService())
                    .build()
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
