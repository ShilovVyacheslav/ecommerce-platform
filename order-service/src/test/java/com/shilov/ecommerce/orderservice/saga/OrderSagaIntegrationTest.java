package com.shilov.ecommerce.orderservice.saga;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.shilov.ecommerce.enums.payment.PaymentStatus;
import com.shilov.ecommerce.events.OrderEventPayload;
import com.shilov.ecommerce.grpc.product.v1.ReservationItem;
import com.shilov.ecommerce.orderservice.dto.OrderCreateDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.entity.OutboxEvent;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.enums.OutboxEventType;
import com.shilov.ecommerce.orderservice.repository.OrderRepository;
import com.shilov.ecommerce.orderservice.repository.OutboxEventRepository;
import com.shilov.ecommerce.orderservice.support.AbstractIntegrationTest;
import com.shilov.ecommerce.orderservice.support.TestOrders;
import com.shilov.ecommerce.orderservice.support.grpc.FakeProductService;
import com.shilov.ecommerce.orderservice.support.grpc.FakeProductService.ReceivedCall;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.INTERNAL_API_KEY_HEADER;
import static com.shilov.ecommerce.orderservice.enums.OrderStatus.CREATED;
import static com.shilov.ecommerce.orderservice.enums.OrderStatus.PAID;
import static com.shilov.ecommerce.orderservice.enums.OrderStatus.STOCK_RESERVED;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step.CHARGE;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step.CONFIRM;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step.REFUND;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step.RELEASE;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step.RESERVE;
import static com.shilov.ecommerce.orderservice.support.SagaCallJournal.call;
import static com.shilov.ecommerce.orderservice.support.wiremock.PaymentServiceCallRecorder.PAYMENTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Order saga (integration)")
public class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final String MANUAL_REVIEW = "Automatic compensation failed - requires manual review: ";
    private static final String RESERVATION_FAILED = "Stock reservation failed: ";
    private static final String PRODUCT_SERVICE_UNAVAILABLE = "Product service is unavailable";
    private static final String INSUFFICIENT_STOCK = "Insufficient stock for one or more products";
    private static final String PRODUCT_NOT_FOUND = "Product was not found";
    private static final String PAYMENT_DECLINED = "Payment declined: ";
    private static final String PAYMENT_UNAVAILABLE = "Payment service unavailable";
    private static final String PAYMENT_INVALID_RESPONSE = "Payment service returned an invalid response";
    private static final String REFUNDED_AFTER_CONFIRMATION_FAILURE = "Payment refunded after stock confirmation failure: ";
    private static final String CONFIRMATION_FAILED = "Stock confirmation failed: ";
    private static final String STOCK_CONFIRMATION_FAILED = "Stock confirmation failed";
    private static final String DECLINE_REASON = "card_declined";

    private static final String REFUND_PATH_PATTERN = PAYMENTS_PATH + "/[0-9a-f-]{36}/refund";
    private static final String COMPLETED_PAYMENT_JSON = """
            {"id": "%s", "amount": 346.47, "currency": "USD", "status": "COMPLETED"}
            """.formatted(UUID.randomUUID());
    private static final String PAYMENT_NOT_FOUND_JSON = """
            {"code": 2001, "message": "No payment found for this order",
             "errorType": "NOT_FOUND_ERROR", "serviceName": "PAYMENT_SERVICE"}
            """;
    private static final Duration BEYOND_GRPC_DEADLINE = Duration.ofMillis(1500);
    private static final int BEYOND_PAYMENT_READ_TIMEOUT_MS = 1500;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OrderSagaOrchestrator orderSagaOrchestrator;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("All steps succeed -> 201 CONFIRMED, each state committed before the next call, ORDER_CONFIRMED in outbox")
        void createOrder_AllStepsSucceed_ConfirmsOrder() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);

            OrderResponseDto response = placeOrder();

            assertConfirmed(response);
            assertThat(JOURNAL.calls()).containsExactly(
                    call(RESERVE, CREATED),
                    call(CHARGE, STOCK_RESERVED),
                    call(CONFIRM, PAID));
            assertThat(orderRepository.findById(response.getId())).get()
                    .extracting(Order::getVersion)
                    .as("one optimistic-lock version per persisted transition")
                    .isEqualTo(3L);
        }

        @Test
        @DisplayName("Order with several lines -> every line reserved, exact order amount charged and stored")
        void createOrder_OrderWithSeveralLines_ReservesEveryLineAndChargesExactAmount() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);

            OrderResponseDto response = placeOrder();

            assertThat(PRODUCT_SERVICE.reserveRequests()).singleElement().satisfies(request -> {
                assertThat(request.getOrderId()).isEqualTo(response.getId().toString());
                assertThat(request.getItemsList())
                        .extracting(ReservationItem::getProductId, ReservationItem::getQuantity)
                        .containsExactlyInAnyOrder(
                                tuple(TestOrders.KEYBOARD_ID, TestOrders.KEYBOARD_QUANTITY),
                                tuple(TestOrders.MOUSE_ID, TestOrders.MOUSE_QUANTITY),
                                tuple(TestOrders.HUB_ID, TestOrders.HUB_QUANTITY));
            });
            PAYMENT_SERVICE.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_PATH))
                    .withRequestBody(equalToJson("""
                            {"orderId": "%s", "amount": 346.47, "currency": "USD"}
                            """.formatted(response.getId()))));
            assertThat(orderRepository.findById(response.getId())).get().satisfies(order -> {
                assertThat(order.getTotalAmount()).isEqualByComparingTo(TestOrders.TOTAL);
                assertThat(order.getCurrency()).isEqualTo(TestOrders.CURRENCY);
                assertThat(order.getItems()).hasSize(3);
            });
        }

        @Test
        @DisplayName("product-service transiently UNAVAILABLE -> retried by the channel, saga completes")
        void createOrder_ReservationTransientlyUnavailable_RetriesAndConfirms() throws Exception {
            PRODUCT_SERVICE.failReserve(Status.UNAVAILABLE, 2);
            stubCharge(PaymentStatus.COMPLETED, null);

            OrderResponseDto response = placeOrder();

            assertConfirmed(response);
            assertThat(PRODUCT_SERVICE.reserveAttempts()).isEqualTo(3);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, RESERVE, RESERVE, CHARGE, CONFIRM);
        }

    }

    @Nested
    @DisplayName("Stock reservation fails")
    class ReservationFails {

        static Stream<Arguments> businessRejections() {
            return Stream.of(
                    arguments(named("insufficient stock (FAILED_PRECONDITION)", Status.FAILED_PRECONDITION),
                            INSUFFICIENT_STOCK),
                    arguments(named("product not found (NOT_FOUND)", Status.NOT_FOUND), PRODUCT_NOT_FOUND));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("businessRejections")
        @DisplayName("Business rejection -> CANCELLED at once, not retried, nothing released, payment untouched")
        void createOrder_ReservationRejected_CancelsWithoutCompensation(Status rejection, String expectedMessage)
                throws Exception {
            PRODUCT_SERVICE.failReserve(rejection);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, RESERVATION_FAILED + expectedMessage);
            assertThat(JOURNAL.calls()).containsExactly(call(RESERVE, CREATED));
            PAYMENT_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DisplayName("Deadline exceeded (outcome unknown) -> not retried, stock released defensively, CANCELLED")
        void createOrder_ReservationDeadlineExceeded_ReleasesStock() throws Exception {
            PRODUCT_SERVICE.delayReserve(BEYOND_GRPC_DEADLINE);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, RESERVATION_FAILED + PRODUCT_SERVICE_UNAVAILABLE);
            assertThat(JOURNAL.calls()).containsExactly(call(RESERVE, CREATED), call(RELEASE, CREATED));
            PAYMENT_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DisplayName("Persistently UNAVAILABLE -> all retry attempts used, then stock released, CANCELLED")
        void createOrder_ReservationPersistentlyUnavailable_ExhaustsRetriesAndReleases() throws Exception {
            PRODUCT_SERVICE.failReserve(Status.UNAVAILABLE);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, RESERVATION_FAILED + PRODUCT_SERVICE_UNAVAILABLE);
            assertThat(PRODUCT_SERVICE.reserveAttempts()).isEqualTo(3);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, RESERVE, RESERVE, RELEASE);
        }

        @Test
        @DisplayName("Outcome unknown + release fails -> CANCELLED and flagged for manual review")
        void createOrder_ReservationOutcomeUnknownAndReleaseFails_FlagsManualReview() throws Exception {
            PRODUCT_SERVICE.delayReserve(BEYOND_GRPC_DEADLINE);
            PRODUCT_SERVICE.failRelease(Status.INTERNAL);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, MANUAL_REVIEW + RESERVATION_FAILED + PRODUCT_SERVICE_UNAVAILABLE);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, RELEASE);
        }

        @Test
        @DisplayName("Outcome unknown + no reservation to release (NOT_FOUND) -> release is a no-op, no manual review")
        void createOrder_ReservationNeverLanded_TreatsMissingReservationAsReleased() throws Exception {
            PRODUCT_SERVICE.delayReserve(BEYOND_GRPC_DEADLINE);
            PRODUCT_SERVICE.failRelease(Status.NOT_FOUND);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, RESERVATION_FAILED + PRODUCT_SERVICE_UNAVAILABLE);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, RELEASE);
        }

    }

    @Nested
    @DisplayName("Payment fails")
    class PaymentFails {

        static Stream<Arguments> unknownChargeOutcomes() {
            return Stream.of(
                    arguments(named("500 Internal Server Error", serverError())),
                    arguments(named("503 Service Unavailable", serviceUnavailable())),
                    arguments(named("409 Conflict", aResponse().withStatus(409))),
                    arguments(named("read timeout after the charge was taken",
                            okJson(COMPLETED_PAYMENT_JSON).withFixedDelay(BEYOND_PAYMENT_READ_TIMEOUT_MS))),
                    arguments(named("connection reset by peer", aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))),
                    arguments(named("malformed JSON body", okJson("{\"status\": "))));
        }

        static Stream<Arguments> invalidPaymentResponses() {
            return Stream.of(
                    arguments(named("empty response body", aResponse().withStatus(201))),
                    arguments(named("response without status",
                            okJson("{\"id\": \"%s\"}".formatted(UUID.randomUUID())))));
        }

        static Stream<Arguments> failedRefunds() {
            return Stream.of(
                    arguments(named("500 Internal Server Error", serverError())),
                    arguments(named("404 without an error contract (e.g. from a proxy)",
                            aResponse().withStatus(404).withBody("<html>Not Found</html>"))),
                    arguments(named("404 from another service",
                            aResponse().withStatus(404)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(PAYMENT_NOT_FOUND_JSON.replace("PAYMENT_SERVICE", "PRODUCT_SERVICE")))),
                    arguments(named("connection reset by peer", aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
        @DisplayName("Payment not COMPLETED -> stock released, CANCELLED, never refunded")
        void createOrder_PaymentNotCompleted_ReleasesStockAndCancels(PaymentStatus paymentStatus) throws Exception {
            stubCharge(paymentStatus, DECLINE_REASON);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_DECLINED + DECLINE_REASON);
            assertThat(JOURNAL.calls()).containsExactly(
                    call(RESERVE, CREATED),
                    call(CHARGE, STOCK_RESERVED),
                    call(RELEASE, STOCK_RESERVED));
        }

        @Test
        @DisplayName("Declined without a reason -> reason is still meaningful")
        void createOrder_DeclinedWithoutReason_UsesUnknownReason() throws Exception {
            stubCharge(PaymentStatus.FAILED, null);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_DECLINED + "unknown");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unknownChargeOutcomes")
        @DisplayName("Charge outcome unknown (money may be taken) -> refund, then release, then CANCELLED")
        void createOrder_ChargeOutcomeUnknown_RefundsThenReleases(ResponseDefinitionBuilder chargeResponse)
                throws Exception {
            stubChargeResponse(chargeResponse);
            stubRefund(okJson("{}"));

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_UNAVAILABLE);
            assertThat(JOURNAL.calls()).containsExactly(
                    call(RESERVE, CREATED),
                    call(CHARGE, STOCK_RESERVED),
                    call(REFUND, STOCK_RESERVED),
                    call(RELEASE, STOCK_RESERVED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPaymentResponses")
        @DisplayName("Invalid payment response -> treated as unknown outcome: refund and release")
        void createOrder_InvalidPaymentResponse_RefundsAndReleases(ResponseDefinitionBuilder chargeResponse)
                throws Exception {
            stubChargeResponse(chargeResponse);
            stubRefund(okJson("{}"));

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_INVALID_RESPONSE);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, REFUND, RELEASE);
        }

        @Test
        @DisplayName("Refund finds no payment (404 PAYMENT_NOT_FOUND) -> charge never landed, compensation succeeds")
        void createOrder_RefundFindsNoPayment_TreatsAsCompensated() throws Exception {
            stubChargeResponse(serverError());
            stubRefund(aResponse().withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody(PAYMENT_NOT_FOUND_JSON));

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_UNAVAILABLE);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, REFUND, RELEASE);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("failedRefunds")
        @DisplayName("Outcome unknown + refund fails -> release still attempted, flagged for manual review")
        void createOrder_ChargeOutcomeUnknownAndRefundFails_ReleasesAndFlagsManualReview(
                ResponseDefinitionBuilder refundResponse) throws Exception {
            stubChargeResponse(serverError());
            stubRefund(refundResponse);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, MANUAL_REVIEW + PAYMENT_UNAVAILABLE);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, REFUND, RELEASE);
        }

        @Test
        @DisplayName("Declined + release persistently UNAVAILABLE -> release retried, then manual review, still no refund")
        void createOrder_DeclinedAndReleaseFails_RetriesReleaseAndFlagsManualReview() throws Exception {
            stubCharge(PaymentStatus.FAILED, DECLINE_REASON);
            PRODUCT_SERVICE.failRelease(Status.UNAVAILABLE);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, MANUAL_REVIEW + PAYMENT_DECLINED + DECLINE_REASON);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, RELEASE, RELEASE, RELEASE);
            assertThat(JOURNAL.count(REFUND)).isZero();
        }

    }

    @Nested
    @DisplayName("Stock confirmation fails after the payment was taken")
    class ConfirmationFails {

        @BeforeEach
        void paymentTakenButConfirmationFails() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);
            PRODUCT_SERVICE.failConfirm(Status.INTERNAL);
        }

        @Test
        @DisplayName("Confirmation fails -> refund, then release, then CANCELLED")
        void createOrder_ConfirmationFails_RefundsThenReleases() throws Exception {
            stubRefund(okJson("{}"));

            OrderResponseDto response = placeOrder();

            assertCancelled(response, REFUNDED_AFTER_CONFIRMATION_FAILURE + STOCK_CONFIRMATION_FAILED);
            assertThat(JOURNAL.calls()).containsExactly(
                    call(RESERVE, CREATED),
                    call(CHARGE, STOCK_RESERVED),
                    call(CONFIRM, PAID),
                    call(REFUND, PAID),
                    call(RELEASE, PAID));
        }

        @ParameterizedTest(name = "refund fails: {0}, release fails: {1}")
        @CsvSource({"true, false", "false, true", "true, true"})
        @DisplayName("Confirmation fails + compensation fails -> both compensations attempted, manual review")
        void createOrder_ConfirmationFailsAndCompensationFails_AttemptsBothAndFlagsManualReview(
                boolean refundFails, boolean releaseFails) throws Exception {
            stubRefund(refundFails ? serverError() : okJson("{}"));
            if (releaseFails) {
                PRODUCT_SERVICE.failRelease(Status.INTERNAL);
            }

            OrderResponseDto response = placeOrder();

            assertCancelled(response, MANUAL_REVIEW + CONFIRMATION_FAILED + STOCK_CONFIRMATION_FAILED);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, CONFIRM, REFUND, RELEASE);
        }

    }

    @Nested
    @DisplayName("Failure reason persistence")
    class FailureReasonPersistence {

        @Test
        @DisplayName("Reason exactly 500 chars -> stored intact in the VARCHAR(500) column")
        void createOrder_ReasonFitsColumnExactly_StoresItIntact() throws Exception {
            String declineReason = "a".repeat(FAILURE_REASON_MAX_LENGTH - PAYMENT_DECLINED.length());
            stubCharge(PaymentStatus.FAILED, declineReason);

            OrderResponseDto response = placeOrder();

            assertCancelled(response, PAYMENT_DECLINED + declineReason);
            assertThat(storedFailureReason(response.getId())).hasSize(FAILURE_REASON_MAX_LENGTH);
        }

        @Test
        @DisplayName("Very long reason -> truncated to the column size, no constraint violation")
        void createOrder_VeryLongReason_TruncatesToColumnSize() throws Exception {
            stubCharge(PaymentStatus.FAILED, "a".repeat(2000));

            OrderResponseDto response = placeOrder();

            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(storedFailureReason(response.getId()))
                    .hasSize(FAILURE_REASON_MAX_LENGTH)
                    .startsWith(PAYMENT_DECLINED)
                    .endsWith("...");
        }

        @Test
        @DisplayName("Very long reason + failed compensation -> manual-review marker survives truncation")
        void createOrder_VeryLongReasonAndCompensationFails_KeepsManualReviewMarker() throws Exception {
            stubCharge(PaymentStatus.FAILED, "a".repeat(2000));
            PRODUCT_SERVICE.failRelease(Status.UNAVAILABLE);

            OrderResponseDto response = placeOrder();

            assertThat(storedFailureReason(response.getId()))
                    .hasSize(FAILURE_REASON_MAX_LENGTH)
                    .startsWith(MANUAL_REVIEW + PAYMENT_DECLINED);
        }

    }

    @Nested
    @DisplayName("Transactional outbox atomicity")
    class OutboxAtomicity {

        @AfterEach
        void dropOutboxFailure() {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_outbox_event ON outbox_events");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_outbox_event()");
        }

        @Test
        @DisplayName("Outbox insert fails on ORDER_CONFIRMED -> 500, order stays PAID, no event, nothing compensated")
        void createOrder_OutboxFailsOnConfirmed_RollsBackTerminalStateWithoutCompensating() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);
            rejectOutboxEvents(OutboxEventType.ORDER_CONFIRMED);

            performPlaceOrder(TestOrders.createRequest()).andExpect(status().isInternalServerError());

            UUID orderId = singleOrderId();
            assertThat(persistedStatus(orderId)).isEqualTo(PAID);
            assertThat(outboxEventRepository.count()).isZero();
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, CONFIRM);
        }

        @Test
        @DisplayName("Outbox insert fails on ORDER_CANCELLED -> 500, order stays STOCK_RESERVED, released exactly once")
        void createOrder_OutboxFailsOnCancelled_RollsBackTerminalStateAfterCompensatingOnce() throws Exception {
            stubCharge(PaymentStatus.FAILED, DECLINE_REASON);
            rejectOutboxEvents(OutboxEventType.ORDER_CANCELLED);

            performPlaceOrder(TestOrders.createRequest()).andExpect(status().isInternalServerError());

            UUID orderId = singleOrderId();
            assertThat(persistedStatus(orderId)).isEqualTo(STOCK_RESERVED);
            assertThat(outboxEventRepository.count()).isZero();
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, RELEASE);
        }

        private void rejectOutboxEvents(OutboxEventType outboxEventType) {
            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION reject_outbox_event() RETURNS trigger AS $$
                    BEGIN
                        RAISE EXCEPTION 'outbox is unavailable';
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            jdbcTemplate.execute("""
                    CREATE TRIGGER reject_outbox_event BEFORE INSERT ON outbox_events
                    FOR EACH ROW WHEN (NEW.event_type = '%s')
                    EXECUTE FUNCTION reject_outbox_event()
                    """.formatted(outboxEventType.name()));
        }

    }

    @Nested
    @DisplayName("Resuming from a persisted state and duplicate delivery")
    class Resumption {

        @Test
        @DisplayName("Resumed from CREATED (detached, outside a transaction) -> items available, full saga")
        void process_ResumedFromCreated_ReservesDetachedItems() throws Exception {
            Order order = persistOrder(CREATED);
            stubCharge(PaymentStatus.COMPLETED, null);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(PRODUCT_SERVICE.reserveRequests()).singleElement()
                    .extracting(request -> request.getItemsList().size())
                    .isEqualTo(3);
            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, CONFIRM);
        }

        @Test
        @DisplayName("Resumed from STOCK_RESERVED -> does not reserve again, completes the saga")
        void process_ResumedFromStockReserved_DoesNotReserveAgain() throws Exception {
            Order order = persistOrder(STOCK_RESERVED);
            stubCharge(PaymentStatus.COMPLETED, null);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(persistedStatus(processedOrder.getId())).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(JOURNAL.calls()).containsExactly(call(CHARGE, STOCK_RESERVED), call(CONFIRM, PAID));
            assertSingleOutboxEvent(processedOrder.getId(), OutboxEventType.ORDER_CONFIRMED);
        }

        @Test
        @DisplayName("Resumed from PAID -> never charges again, only confirms")
        void process_ResumedFromPaid_DoesNotChargeAgain() {
            Order order = persistOrder(PAID);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(persistedStatus(processedOrder.getId())).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(JOURNAL.calls()).containsExactly(call(CONFIRM, PAID));
            PAYMENT_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DisplayName("Resumed from PAID + confirmation fails -> refunds and releases")
        void process_ResumedFromPaidAndConfirmationFails_Compensates() {
            Order order = persistOrder(PAID);
            PRODUCT_SERVICE.failConfirm(Status.INTERNAL);
            stubRefund(okJson("{}"));

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(persistedStatus(processedOrder.getId())).isEqualTo(OrderStatus.CANCELLED);
            assertThat(storedFailureReason(processedOrder.getId()))
                    .isEqualTo(REFUNDED_AFTER_CONFIRMATION_FAILURE + STOCK_CONFIRMATION_FAILED);
            assertThat(JOURNAL.calls()).containsExactly(call(CONFIRM, PAID), call(REFUND, PAID), call(RELEASE, PAID));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "CANCELLED"})
        @DisplayName("Order already in a terminal state -> no calls, no writes, no duplicate events")
        void process_TerminalOrder_DoesNothing(OrderStatus terminalStatus) {
            Order order = persistOrder(terminalStatus);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getStatus()).isEqualTo(terminalStatus);
            assertThat(orderRepository.findById(order.getId())).get()
                    .extracting(Order::getVersion)
                    .isEqualTo(order.getVersion());
            assertThat(JOURNAL.calls()).isEmpty();
            assertThat(outboxEventRepository.count()).isZero();
        }

        @Test
        @DisplayName("Completed order processed again -> charged once, confirmed once, one event")
        void process_DuplicateDelivery_HasNoAdditionalSideEffects() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);
            OrderResponseDto response = placeOrder();

            orderSagaOrchestrator.process(orderRepository.findById(response.getId()).orElseThrow());

            assertThat(JOURNAL.steps()).containsExactly(RESERVE, CHARGE, CONFIRM);
            assertSingleOutboxEvent(response.getId(), OutboxEventType.ORDER_CONFIRMED);
        }

    }

    @Nested
    @DisplayName("Concurrent processing of the same order")
    class ConcurrentProcessing {

        @Test
        @DisplayName("Stale copy of an already processed order -> optimistic lock rejects it, customer charged once")
        void process_StaleCopyOfProcessedOrder_RejectedByOptimisticLock() throws Exception {
            Order order = persistOrder(CREATED);
            Order staleCopy = orderRepository.findById(order.getId()).orElseThrow();
            stubCharge(PaymentStatus.COMPLETED, null);
            orderSagaOrchestrator.process(order);

            assertThatThrownBy(() -> orderSagaOrchestrator.process(staleCopy))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            assertThat(persistedStatus(order.getId())).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(JOURNAL.count(CHARGE)).isEqualTo(1);
            assertThat(JOURNAL.steps())
                    .as("the stale copy re-sends only the idempotent per-order reservation before being rejected")
                    .containsExactly(RESERVE, CHARGE, CONFIRM, RESERVE);
            assertSingleOutboxEvent(order.getId(), OutboxEventType.ORDER_CONFIRMED);
        }

    }

    @Nested
    @DisplayName("Internal authentication and tracing across services")
    class CrossServiceHeaders {

        @BeforeEach
        void sagaTouchesEveryEndpoint() throws Exception {
            stubCharge(PaymentStatus.COMPLETED, null);
            PRODUCT_SERVICE.failConfirm(Status.INTERNAL);
            stubRefund(okJson("{}"));
        }

        @Test
        @DisplayName("Every call to payment-service and product-service carries the internal API key")
        void createOrder_AnyOutboundCall_CarriesInternalApiKey() throws Exception {
            placeOrder();

            PAYMENT_SERVICE.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_PATH))
                    .withHeader(INTERNAL_API_KEY_HEADER, equalTo(INTERNAL_API_KEY)));
            PAYMENT_SERVICE.verify(1, postRequestedFor(urlMatching(REFUND_PATH_PATTERN))
                    .withHeader(INTERNAL_API_KEY_HEADER, equalTo(INTERNAL_API_KEY)));
            assertThat(PRODUCT_SERVICE.receivedCalls())
                    .extracting(ReceivedCall::method)
                    .contains("GetProduct", "ReserveStock", "ConfirmReservation", "ReleaseReservation");
            assertThat(PRODUCT_SERVICE.receivedCalls())
                    .extracting(ReceivedCall::apiKey)
                    .containsOnly(INTERNAL_API_KEY);
        }

        @Test
        @DisplayName("Incoming X-Correlation-Id -> propagated to product lookups, every saga step, compensation and the outbox")
        void createOrder_WithCorrelationId_PropagatesItToEveryOutboundCall() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            placeOrder(request -> request.header(CORRELATION_ID_HEADER, correlationId));

            PAYMENT_SERVICE.verify(2, postRequestedFor(urlMatching(PAYMENTS_PATH + ".*"))
                    .withHeader(CORRELATION_ID_HEADER, equalTo(correlationId)));
            assertThat(PRODUCT_SERVICE.receivedCalls())
                    .extracting(ReceivedCall::method, ReceivedCall::correlationId)
                    .containsExactlyInAnyOrder(
                            tuple("GetProduct", correlationId),
                            tuple("GetProduct", correlationId),
                            tuple("GetProduct", correlationId),
                            tuple("ReserveStock", correlationId),
                            tuple("ConfirmReservation", correlationId),
                            tuple("ReleaseReservation", correlationId));
            assertThat(outboxEventRepository.findAll()).singleElement()
                    .as("stored with the event, so the Kafka consumer continues the same correlation id")
                    .extracting(OutboxEvent::getCorrelationId)
                    .isEqualTo(correlationId);
        }

    }

    @Nested
    @DisplayName("Order rejected before the saga starts")
    class RejectedBeforeSaga {

        static Stream<Arguments> unavailableProducts() {
            return Stream.of(
                    arguments(named("product does not exist",
                            (Consumer<FakeProductService>) fake -> fake.removeFromCatalog(TestOrders.MOUSE_ID))),
                    arguments(named("product is inactive",
                            (Consumer<FakeProductService>) fake -> fake.deactivate(TestOrders.MOUSE_ID))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unavailableProducts")
        @DisplayName("Unavailable product -> 409, nothing persisted, saga never started")
        void createOrder_ProductUnavailable_RejectsWithoutStartingSaga(Consumer<FakeProductService> makeUnavailable)
                throws Exception {
            makeUnavailable.accept(PRODUCT_SERVICE);

            performPlaceOrder(TestOrders.createRequest())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(3002));

            assertNothingHappened();
        }

        @Test
        @DisplayName("Order without items -> 400, nothing persisted, saga never started")
        void createOrder_WithoutItems_RejectsWithoutStartingSaga() throws Exception {
            performPlaceOrder(OrderCreateDto.builder().items(List.of()).build())
                    .andExpect(status().isBadRequest());

            assertNothingHappened();
        }

        private void assertNothingHappened() {
            assertThat(orderRepository.count()).isZero();
            assertThat(outboxEventRepository.count()).isZero();
            assertThat(JOURNAL.calls()).isEmpty();
            PAYMENT_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        }

    }

    private void stubCharge(PaymentStatus paymentStatus, String failureReason) throws Exception {
        PaymentResponseDto payment = PaymentResponseDto.builder()
                .id(UUID.randomUUID())
                .amount(TestOrders.TOTAL)
                .currency(TestOrders.CURRENCY)
                .status(paymentStatus)
                .failureReason(failureReason)
                .build();
        stubChargeResponse(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(payment)));
    }

    private void stubChargeResponse(ResponseDefinitionBuilder response) {
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(PAYMENTS_PATH)).willReturn(response));
    }

    private void stubRefund(ResponseDefinitionBuilder response) {
        PAYMENT_SERVICE.stubFor(post(urlMatching(REFUND_PATH_PATTERN)).willReturn(response));
    }

    private Order persistOrder(OrderStatus orderStatus) {
        UUID orderId = orderRepository.save(TestOrders.unsaved(orderStatus)).getId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    private UUID singleOrderId() {
        return jdbcTemplate.queryForObject("SELECT id FROM orders", UUID.class);
    }

    private String storedFailureReason(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT failure_reason FROM orders WHERE id = ?", String.class, orderId);
    }

    private void assertConfirmed(OrderResponseDto response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getFailureReason()).isNull();
        assertThat(persistedStatus(response.getId())).isEqualTo(OrderStatus.CONFIRMED);

        OrderEventPayload event = assertSingleOutboxEvent(response.getId(), OutboxEventType.ORDER_CONFIRMED);
        assertThat(event.getStatus()).isEqualTo(OrderStatus.CONFIRMED.name());
        assertThat(event.getUserId()).isEqualTo(TestOrders.USER_ID);
        assertThat(event.getTotalAmount()).isEqualByComparingTo(TestOrders.TOTAL);
        assertThat(event.getCurrency()).isEqualTo(TestOrders.CURRENCY);
        assertThat(event.getFailureReason()).isNull();
    }

    private void assertCancelled(OrderResponseDto response, String expectedReason) throws Exception {
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getFailureReason()).isEqualTo(expectedReason);
        assertThat(persistedStatus(response.getId())).isEqualTo(OrderStatus.CANCELLED);
        assertThat(storedFailureReason(response.getId())).isEqualTo(expectedReason);

        OrderEventPayload event = assertSingleOutboxEvent(response.getId(), OutboxEventType.ORDER_CANCELLED);
        assertThat(event.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(event.getFailureReason()).isEqualTo(expectedReason);
    }

    private OrderEventPayload assertSingleOutboxEvent(UUID orderId, OutboxEventType outboxEventType) throws Exception {
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getAggregateType()).isEqualTo("Order");
            assertThat(event.getAggregateId()).isEqualTo(orderId);
            assertThat(event.getEventType()).isEqualTo(outboxEventType.name());
            assertThat(event.getPublishedAt()).isNull();
        });
        OrderEventPayload payload = objectMapper.readValue(events.getFirst().getPayload(), OrderEventPayload.class);
        assertThat(payload.getOrderId()).isEqualTo(orderId);
        return payload;
    }

}
