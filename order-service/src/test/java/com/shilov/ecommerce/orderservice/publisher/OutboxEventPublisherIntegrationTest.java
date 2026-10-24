package com.shilov.ecommerce.orderservice.publisher;

import com.shilov.ecommerce.enums.payment.PaymentStatus;
import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.entity.OutboxEvent;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import com.shilov.ecommerce.orderservice.support.AbstractIntegrationTest;
import com.shilov.ecommerce.orderservice.support.TestOrders;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.orderservice.support.wiremock.PaymentServiceCallRecorder.PAYMENTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Outbox event publisher (integration)")
@TestPropertySource(properties = "messaging.kafka.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OutboxEventPublisherIntegrationTest extends AbstractIntegrationTest {

    private static final String TOPIC = "order-events";
    private static final String DECLINE_REASON = "card_declined";
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration MORE_THAN_ONE_POLLING_CYCLE = Duration.ofSeconds(5);

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        KAFKA.start();
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private KafkaConsumer<String, String> consumer;

    private final List<ConsumerRecord<String, String>> receivedRecords = new ArrayList<>();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(TOPIC));
        receivedRecords.clear();
    }

    @AfterEach
    void unsubscribe() {
        consumer.close();
    }

    @Test
    @DisplayName("Confirmed order -> ORDER_CONFIRMED delivered to order-events keyed by order id, row marked published")
    void publishPendingEvents_ConfirmedOrder_DeliversEventAndMarksItPublished() throws Exception {
        stubCharge(PaymentStatus.COMPLETED, null);

        OrderResponseDto response = placeOrder();

        ConsumerRecord<String, String> record = awaitSingleRecord(response.getId());
        OrderEventPayload payload = objectMapper.readValue(record.value(), OrderEventPayload.class);
        assertThat(payload.getOrderId()).isEqualTo(response.getId());
        assertThat(payload.getStatus()).isEqualTo(OrderStatus.CONFIRMED.name());
        assertThat(payload.getTotalAmount()).isEqualByComparingTo(TestOrders.TOTAL);
        assertThat(payload.getFailureReason()).isNull();
        awaitAllOutboxEventsPublished();
    }

    @Test
    @DisplayName("Cancelled order -> ORDER_CANCELLED delivered with the failure reason")
    void publishPendingEvents_CancelledOrder_DeliversEventWithFailureReason() throws Exception {
        stubCharge(PaymentStatus.FAILED, DECLINE_REASON);

        OrderResponseDto response = placeOrder();

        ConsumerRecord<String, String> record = awaitSingleRecord(response.getId());
        OrderEventPayload payload = objectMapper.readValue(record.value(), OrderEventPayload.class);
        assertThat(payload.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(payload.getFailureReason()).isEqualTo("Payment declined: " + DECLINE_REASON);
        awaitAllOutboxEventsPublished();
    }

    @Test
    @DisplayName("Request with X-Correlation-Id -> the event carries it in the X-Correlation-Id record header")
    void publishPendingEvents_RequestWithCorrelationId_PropagatesItInRecordHeader() throws Exception {
        stubCharge(PaymentStatus.COMPLETED, null);
        String correlationId = UUID.randomUUID().toString();

        OrderResponseDto response = placeOrder(request -> request.header(CORRELATION_ID_HEADER, correlationId));

        Header header = awaitSingleRecord(response.getId()).headers().lastHeader(CORRELATION_ID_HEADER);
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Several orders over several polling cycles -> every event delivered exactly once")
    void publishPendingEvents_SeveralPollingCycles_DeliversEveryEventExactlyOnce() throws Exception {
        stubCharge(PaymentStatus.COMPLETED, null);
        List<String> orderIds = List.of(
                placeOrder().getId().toString(),
                placeOrder().getId().toString(),
                placeOrder().getId().toString());

        await().atMost(DELIVERY_TIMEOUT).untilAsserted(() -> {
            pollRecords();
            assertThat(receivedRecords).extracting(ConsumerRecord::key).containsAll(orderIds);
        });
        awaitAllOutboxEventsPublished();
        await().pollDelay(MORE_THAN_ONE_POLLING_CYCLE).atMost(MORE_THAN_ONE_POLLING_CYCLE.plusSeconds(5))
                .untilAsserted(() -> {
                    pollRecords();
                    assertThat(receivedRecords).extracting(ConsumerRecord::key)
                            .filteredOn(orderIds::contains)
                            .containsExactlyInAnyOrderElementsOf(orderIds);
                });
    }

    private ConsumerRecord<String, String> awaitSingleRecord(UUID orderId) {
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(() -> {
            pollRecords();
            assertThat(recordsFor(orderId)).isNotEmpty();
        });
        assertThat(recordsFor(orderId)).hasSize(1);
        return recordsFor(orderId).getFirst();
    }

    private List<ConsumerRecord<String, String>> recordsFor(UUID orderId) {
        return receivedRecords.stream().filter(record -> orderId.toString().equals(record.key())).toList();
    }

    private void pollRecords() {
        consumer.poll(Duration.ofMillis(500)).forEach(receivedRecords::add);
    }

    private void awaitAllOutboxEventsPublished() {
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(() -> assertThat(outboxEventRepository.findAll())
                .isNotEmpty()
                .extracting(OutboxEvent::getPublishedAt)
                .doesNotContainNull());
    }

    private void stubCharge(PaymentStatus paymentStatus, String failureReason) throws Exception {
        PaymentResponseDto payment = PaymentResponseDto.builder()
                .id(UUID.randomUUID())
                .amount(TestOrders.TOTAL)
                .currency(TestOrders.CURRENCY)
                .status(paymentStatus)
                .failureReason(failureReason)
                .build();
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(PAYMENTS_PATH)).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(payment))));
    }

}
