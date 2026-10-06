package com.shilov.ecommerce.orderservice.saga;

import com.shilov.ecommerce.enums.payment.PaymentStatus;
import com.shilov.ecommerce.orderservice.client.PaymentServiceClient;
import com.shilov.ecommerce.orderservice.client.ProductServiceClient;
import com.shilov.ecommerce.orderservice.dto.client.PaymentResponseDto;
import com.shilov.ecommerce.orderservice.dto.client.ReservationItemRequestDto;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.enums.OrderStatus;
import com.shilov.ecommerce.orderservice.enums.OutboxEventType;
import com.shilov.ecommerce.orderservice.exception.SagaStepException;
import com.shilov.ecommerce.orderservice.repository.OrderRepository;
import com.shilov.ecommerce.orderservice.service.OutboxService;
import com.shilov.ecommerce.orderservice.support.TestOrders;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.in;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static wiremock.org.eclipse.jetty.util.component.Dumpable.named;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaOrchestrator")
public class OrderSagaOrchestratorTest {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final String MANUAL_REVIEW = "Automatic compensation failed - requires manual review: ";
    private static final String RESERVATION_FAILED = "Stock reservation failed: ";
    private static final String PAYMENT_DECLINED = "Payment declined: ";
    private static final String PAYMENT_UNAVAILABLE = "Payment service unavailable";
    private static final String PAYMENT_INVALID_RESPONSE = "Payment service returned an invalid response";
    private static final String REFUNDED_AFTER_CONFIRMATION_FAILURE = "Payment refunded after stock confirmation failure: ";
    private static final String CONFIRMATION_FAILED = "Stock confirmation failed: ";
    private static final String DECLINE_REASON = "card_declined";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Captor
    private ArgumentCaptor<List<ReservationItemRequestDto>> reservationItemsCaptor;

    private OrderSagaOrchestrator orderSagaOrchestrator;

    private Order order;

    private final List<OrderStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void init() {
        orderSagaOrchestrator = new OrderSagaOrchestrator(
                orderRepository, outboxService, productServiceClient, paymentServiceClient);
        order = TestOrders.created();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("All steps succeed -> CONFIRMED, every state persisted, ORDER_CONFIRMED published")
        void process_AllStepsSucceed_ConfirmsOrder() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder).isSameAs(order);
            assertThat(processedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(processedOrder.getFailureReason()).isNull();
            assertThat(savedStatuses).containsExactly(OrderStatus.STOCK_RESERVED, OrderStatus.PAID);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Order with several lines -> every line is reserved with its quantity")
        void process_OrderWithSeveralLines_ReservesEveryLine() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            orderSagaOrchestrator.process(order);

            verify(productServiceClient).reserve(eq(order.getId().toString()), reservationItemsCaptor.capture());
            assertThat(reservationItemsCaptor.getValue())
                    .extracting(ReservationItemRequestDto::getProductId, ReservationItemRequestDto::getQuantity)
                    .containsExactlyInAnyOrder(
                            tuple(TestOrders.KEYBOARD_ID, TestOrders.KEYBOARD_QUANTITY),
                            tuple(TestOrders.MOUSE_ID, TestOrders.MOUSE_QUANTITY),
                            tuple(TestOrders.HUB_ID, TestOrders.HUB_QUANTITY));
        }

        @Test
        @DisplayName("Order with its own total and currency -> exactly that amount is charged")
        void process_CustomTotalAndCurrency_ChargeExactOrderAmount() {
            order.setTotalAmount(new BigDecimal("123.45"));
            order.setCurrency("EUR");
            stubSave(order);

            when(paymentServiceClient.charge(order.getId(), new BigDecimal("123.45"), "EUR"))
                    .thenReturn(payment(PaymentStatus.COMPLETED, null));
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            orderSagaOrchestrator.process(order);

            verify(paymentServiceClient).charge(order.getId(), new BigDecimal("123.45"), "EUR");
        }

        @Test
        @DisplayName("save() returns a new instance (JPA merge) -> saga continues with the returned instance")
        void process_PersistenceReturnsNewInstance_ContinuesWithReturnedInstance() {
            Order orderReserved = copyOf(order, OrderStatus.STOCK_RESERVED, 1L);
            Order orderPaid = copyOf(order, OrderStatus.PAID, 2L);
            Order orderConfirmed = copyOf(order, OrderStatus.CONFIRMED, 3L);

            when(orderRepository.save(order)).thenReturn(orderReserved);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            when(orderRepository.save(orderReserved)).thenReturn(orderPaid);
            when(outboxService.saveAndPublish(orderPaid, OutboxEventType.ORDER_CONFIRMED)).thenReturn(orderConfirmed);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder).isSameAs(orderConfirmed);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(orderReserved);
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(orderPaid, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

    }

    @Nested
    @DisplayName("Stock reservation fails")
    class ReservationFails {

        static Stream<Arguments> businessRejections() {
            return Stream.of(
                    arguments(named("insufficient stock", SagaStepException.insufficientStock())),
                    arguments(named("product not found", SagaStepException.productNotFound()))
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("businessRejections")
        @DisplayName("Business rejection -> CANCELLED at once, nothing to compensate, payment untouched")
        void process_ReservationRejected_CancelsWithoutCompensation(SagaStepException rejection) {
            doThrow(rejection).when(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, RESERVATION_FAILED + rejection.getMessage());

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Outcome unknown (timeout) -> stock is released defensively, then CANCELLED")
        void process_ReservationOutcomeUnknown_ReleasesStock() {
            SagaStepException timeout =
                    SagaStepException.productServiceUnavailable(Status.DEADLINE_EXCEEDED.asRuntimeException());
            doThrow(timeout).when(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, RESERVATION_FAILED + timeout.getMessage());

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(productServiceClient).release(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Outcome unknown + release fails -> CANCELLED and flagged for manual review")
        void process_ReservationOutcomeUnknown_ReleasesStockAndFlaggedForManualReview() {
            SagaStepException unavailable =
                    SagaStepException.productServiceUnavailable(Status.UNAVAILABLE.asRuntimeException());
            doThrow(unavailable).when(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            doThrow(SagaStepException.stockReleaseFailed(Status.UNAVAILABLE.asRuntimeException()))
                    .when(productServiceClient).release(order.getId().toString());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, MANUAL_REVIEW + RESERVATION_FAILED + unavailable.getMessage());
            verify(productServiceClient).release(order.getId().toString());
            verifyNoInteractions(paymentServiceClient);
        }

    }

    @Nested
    @DisplayName("Payment fails")
    class PaymentFails {

        static Stream<Arguments> invalidPaymentResponses() {
            return Stream.of(
                    arguments(named("empty response body", (PaymentResponseDto) null)),
                    arguments(named("response without status", PaymentResponseDto.builder().id(UUID.randomUUID()).build())));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
        @DisplayName("Payment not COMPLETED -> stock released, CANCELLED, never refunded")
        void process_PaymentNotCompleted_ReleasesStockAndCancels(PaymentStatus status) {
            stubSave(order);
            stubCharge(order, payment(status, DECLINE_REASON));
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_DECLINED + DECLINE_REASON);
            assertThat(savedStatuses).containsExactly(OrderStatus.STOCK_RESERVED);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(productServiceClient).release(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Declined without a reason -> reason is still meaningful")
        void process_DeclinedWithoutReason_UsesUnknownReason() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, null));
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_DECLINED + "unknown");
        }

        @Test
        @DisplayName("Declined + release fails -> CANCELLED, flagged for manual review, still no refund")
        void process_DeclinedAndReleaseFails_FlagsManualReview() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, DECLINE_REASON));
            doThrow(SagaStepException.stockReleaseFailed(Status.UNAVAILABLE.asRuntimeException()))
                    .when(productServiceClient).release(order.getId().toString());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, MANUAL_REVIEW + PAYMENT_DECLINED + DECLINE_REASON);
            verify(productServiceClient).release(order.getId().toString());
            verify(paymentServiceClient, never()).refund(any());
        }

        @Test
        @DisplayName("Compensation throws an unexpected exception -> it is contained, order flagged for manual review")
        void process_ReleaseThrowsUnexpectedException_ContainsIt() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, DECLINE_REASON));
            doThrow(new IllegalStateException("unexpected client failure"))
                    .when(productServiceClient).release(order.getId().toString());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, MANUAL_REVIEW + PAYMENT_DECLINED + DECLINE_REASON);
        }

        @Test
        @DisplayName("Payment timed out (money may be taken) -> refund and release, then CANCELLED")
        void process_PaymentTimedOut_RefundsAndReleases() {
            stubSave(order);
            stubChargeTimeout(order);
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_UNAVAILABLE);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(paymentServiceClient).refund(order.getId());
            inOrder.verify(productServiceClient).release(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPaymentResponses")
        @DisplayName("Invalid payment response -> treated as unknown outcome: refund and release, no NPE")
        void process_InvalidPaymentResponse_RefundsAndReleases(PaymentResponseDto paymentResponseDto) {
            stubSave(order);
            stubCharge(order, paymentResponseDto);
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_INVALID_RESPONSE);
            verify(paymentServiceClient).refund(order.getId());
            verify(productServiceClient).release(order.getId().toString());
        }

        @ParameterizedTest(name = "refund fails: {0}, release fails: {1}")
        @CsvSource({"true, false", "false, true", "true, true"})
        @DisplayName("Payment timed out + compensation fails -> both compensations attempted, manual review")
        void process_PaymentTimedOutAndCompensationFails_AttemptsBothAndFlagsManualReview(
                boolean refundFails, boolean releaseFails) {
            stubSave(order);
            stubChargeTimeout(order);
            stubCompensationFailures(refundFails, releaseFails);
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, MANUAL_REVIEW + PAYMENT_UNAVAILABLE);
            verify(paymentServiceClient).refund(order.getId());
            verify(productServiceClient).release(order.getId().toString());
        }

    }

    @Nested
    @DisplayName("Stock confirmation fails after the payment was taken")
    class ConfirmationFails {

        private final SagaStepException confirmationFailure =
                SagaStepException.stockConfirmationFailed(Status.INTERNAL.asRuntimeException());

        @BeforeEach
        void paymentTakenButConfirmationFails() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            doThrow(confirmationFailure).when(productServiceClient).confirm(order.getId().toString());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);
        }

        @Test
        @DisplayName("Confirmation fails -> refund, then release, then CANCELLED")
        void process_ConfirmationFails_RefundsThenReleases() {
            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder,
                    REFUNDED_AFTER_CONFIRMATION_FAILURE + confirmationFailure.getMessage());
            assertThat(savedStatuses).containsExactly(OrderStatus.STOCK_RESERVED, OrderStatus.PAID);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(paymentServiceClient).refund(order.getId());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @ParameterizedTest(name = "refund fails: {0}, release fails: {1}")
        @CsvSource({"true, false", "false, true", "true, true"})
        @DisplayName("Confirmation fails + compensation fails -> both compensations attempted, manual review")
        void process_ConfirmationFailsAndCompensationFails_AttemptsBothAndFlagsManualReview(
                boolean refundFails, boolean releaseFails) {
            stubCompensationFailures(refundFails, releaseFails);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder,
                    MANUAL_REVIEW + CONFIRMATION_FAILED + confirmationFailure.getMessage());
            verify(paymentServiceClient).refund(order.getId());
            verify(productServiceClient).release(order.getId().toString());
        }

    }

    @Nested
    @DisplayName("Failure reason length")
    class FailureReasonLength {

        @Test
        @DisplayName("Reason exactly 500 chars -> kept intact")
        void process_ReasonFitsColumnExactly_KeepsItIntact() {
            String declineReason = "a".repeat(FAILURE_REASON_MAX_LENGTH - PAYMENT_DECLINED.length());
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, declineReason));
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_DECLINED + declineReason);
            assertThat(processedOrder.getFailureReason()).hasSize(FAILURE_REASON_MAX_LENGTH);
        }

        @Test
        @DisplayName("Reason 501 chars -> truncated to 500 and marked with an ellipsis")
        void process_ReasonOneCharTooLong_TruncatesWithEllipsis() {
            String declineReason = "a".repeat(FAILURE_REASON_MAX_LENGTH - PAYMENT_DECLINED.length() + 1);
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, declineReason));
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getFailureReason())
                    .hasSize(FAILURE_REASON_MAX_LENGTH)
                    .startsWith(PAYMENT_DECLINED)
                    .endsWith("...");
        }

        @Test
        @DisplayName("Long reason + failed compensation -> manual-review marker survives truncation")
        void process_LongReasonAndCompensationFails_KeepsManualReviewMarker() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, "a".repeat(2000)));
            doThrow(SagaStepException.stockReleaseFailed(Status.UNAVAILABLE.asRuntimeException()))
                    .when(productServiceClient).release(order.getId().toString());
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getFailureReason())
                    .hasSize(FAILURE_REASON_MAX_LENGTH)
                    .startsWith(MANUAL_REVIEW + PAYMENT_DECLINED);
        }

    }

    @Nested
    @DisplayName("Own infrastructure (database, outbox) fails or unexpected exception")
    class InfrastructureFailsOrUnexpectedException {

        private final DataAccessResourceFailureException dbDown =
                new DataAccessResourceFailureException("database is unavailable");

        @Test
        @DisplayName("Saving STOCK_RESERVED fails -> exception propagates, customer is never charged")
        void process_SavingStockReservedFails_ThrowsAndNeverCharges() {
            when(orderRepository.save(order)).thenThrow(dbDown);

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(dbDown);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Saving PAID fails -> exception propagates, no confirmation, no compensation")
        void process_SavingPaidFails_ThrowsWithoutConfirmingOrCompensating() {
            when(orderRepository.save(order)).thenReturn(order).thenThrow(dbDown);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(dbDown);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(order);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Publishing ORDER_CONFIRMED fails -> exception propagates, completed checkout is not compensated")
        void process_PublishingConfirmedFails_ThrowsWithoutCompensating() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            when(outboxService.saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED)).thenThrow(dbDown);

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(dbDown);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Publishing ORDER_CANCELLED fails -> exception propagates after compensating exactly once")
        void process_PublishingCancelledFails_ThrowsAfterCompensatingOnce() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.FAILED, DECLINE_REASON));
            when(outboxService.saveAndPublish(order, OutboxEventType.ORDER_CANCELLED)).thenThrow(dbDown);

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(dbDown);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(productServiceClient).release(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, TestOrders.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Reserve throws an unexpected exception -> it propagates, nothing else happens")
        void process_ReserveThrowsUnexpectedException_Propagates() {
            IllegalStateException bug = new IllegalStateException("unexpected client failure");
            doThrow(bug).when(productServiceClient).reserve(eq(order.getId().toString()), anyList());

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(bug);

            verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Charge throws an unexpected exception -> it propagates, no compensation")
        void process_ChargeThrowsUnexpectedException_PropagatesWithoutCompensation() {
            IllegalStateException bug = new IllegalStateException("unexpected client failure");
            stubSave(order);
            when(paymentServiceClient.charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY)).thenThrow(bug);

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(bug);

            verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            verify(orderRepository).save(order);
            verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Confirm throws an unexpected exception -> it propagates, no refund, no release")
        void process_ConfirmThrowsUnexpectedException_PropagatesWithoutCompensation() {
            IllegalStateException bug = new IllegalStateException("unexpected client failure");
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            doThrow(bug).when(productServiceClient).confirm(order.getId().toString());

            assertThatThrownBy(() -> orderSagaOrchestrator.process(order)).isSameAs(bug);

            verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            verify(orderRepository, times(2)).save(order);
            verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            verify(productServiceClient).confirm(order.getId().toString());
            verifyNoMoreCalls();
        }

    }

    @Nested
    @DisplayName("Resuming from a persisted state and duplicate delivery")
    class Resumption {

        @Test
        @DisplayName("Resumed from STOCK_RESERVED -> does not reserve again, completes the saga")
        void process_ResumedFromStockReserved_DoesNotReserveAgain() {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            InOrder inOrder = inOrderAll();
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(orderRepository).save(order);
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Resumed from STOCK_RESERVED + declined -> releases the earlier reservation")
        void process_ResumedFromStockReservedAndDeclined_ReleasesStock() {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            stubCharge(order, payment(PaymentStatus.FAILED, DECLINE_REASON));
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, PAYMENT_DECLINED + DECLINE_REASON);

            InOrder inOrder = inOrderAll();
            inOrder.verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            inOrder.verify(productServiceClient).release(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Resumed from PAID -> never charges again, only confirms")
        void process_ResumedFromPaid_DoesNotChargeAgain() {
            order.setStatus(OrderStatus.PAID);
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).confirm(order.getId().toString());
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

        @Test
        @DisplayName("Resumed from PAID + confirmation fails -> refunds and releases")
        void process_ResumedFromPaidAndConfirmationFails_Compensates() {
            order.setStatus(OrderStatus.PAID);
            String paidOrderId = order.getId().toString();
            SagaStepException failure = SagaStepException.stockConfirmationFailed(Status.INTERNAL.asRuntimeException());
            doThrow(failure).when(productServiceClient).confirm(paidOrderId);
            stubPublish(order, OutboxEventType.ORDER_CANCELLED);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertCancelled(processedOrder, REFUNDED_AFTER_CONFIRMATION_FAILURE + failure.getMessage());

            InOrder inOrder = inOrderAll();
            inOrder.verify(productServiceClient).confirm(paidOrderId);
            inOrder.verify(paymentServiceClient).refund(order.getId());
            inOrder.verify(productServiceClient).release(paidOrderId);
            inOrder.verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CANCELLED);
            verifyNoMoreCalls();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "CANCELLED"})
        @DisplayName("Order already in a terminal state -> no calls, no writes, no duplicate events")
        void process_TerminalOrder_DoesNothing(OrderStatus terminalStatus) {
            order.setStatus(terminalStatus);

            Order processedOrder = orderSagaOrchestrator.process(order);

            assertThat(processedOrder).isSameAs(order);
            assertThat(processedOrder.getStatus()).isEqualTo(terminalStatus);
            verifyNoInteractions(productServiceClient, paymentServiceClient, orderRepository, outboxService);
        }

        @Test
        @DisplayName("Same order processed twice -> charged once, confirmed once, one event")
        void process_DuplicateDelivery_HasNoAdditionalSideEffects() {
            stubSave(order);
            stubCharge(order, payment(PaymentStatus.COMPLETED, null));
            stubPublish(order, OutboxEventType.ORDER_CONFIRMED);

            Order firstProcessedOrder = orderSagaOrchestrator.process(order);
            Order secondProcessedOrder = orderSagaOrchestrator.process(firstProcessedOrder);

            assertThat(secondProcessedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(productServiceClient).reserve(eq(order.getId().toString()), anyList());
            verify(orderRepository, times(2)).save(order);
            verify(paymentServiceClient).charge(order.getId(), TestOrders.TOTAL, TestOrders.CURRENCY);
            verify(productServiceClient).confirm(order.getId().toString());
            verify(outboxService).saveAndPublish(order, OutboxEventType.ORDER_CONFIRMED);
            verifyNoMoreCalls();
        }

    }

    private void stubSave(Order orderToSave) {
        when(orderRepository.save(orderToSave)).thenAnswer(invocation -> {
           savedStatuses.add(orderToSave.getStatus());
           return orderToSave;
        });
    }

    private void stubCharge(Order orderToCharge, PaymentResponseDto paymentResponseDto) {
        when(paymentServiceClient
                .charge(orderToCharge.getId(), orderToCharge.getTotalAmount(), orderToCharge.getCurrency()))
                .thenReturn(paymentResponseDto);
    }

    private void stubChargeTimeout(Order orderToCharge) {
        when(paymentServiceClient
                .charge(orderToCharge.getId(), orderToCharge.getTotalAmount(), orderToCharge.getCurrency()))
                .thenThrow(SagaStepException.paymentServiceUnavailable(
                        new ResourceAccessException("Read timed out")));
    }

    private void stubPublish(Order orderToPublish, OutboxEventType outboxEventType) {
        when(outboxService.saveAndPublish(orderToPublish, outboxEventType)).thenReturn(orderToPublish);
    }

    private void stubCompensationFailures(boolean refundFails, boolean releaseFails) {
        if (refundFails) {
            doThrow(SagaStepException.refundFailed(new ResourceAccessException("Connection refused")))
                    .when(paymentServiceClient).refund(order.getId());
        }
        if (releaseFails) {
            doThrow(SagaStepException.stockReleaseFailed(Status.UNAVAILABLE.asRuntimeException()))
                    .when(productServiceClient).release(order.getId().toString());
        }
    }

    private static PaymentResponseDto payment(PaymentStatus paymentStatus, String failureReason) {
        return PaymentResponseDto.builder()
                .id(UUID.randomUUID())
                .amount(TestOrders.TOTAL)
                .currency(TestOrders.CURRENCY)
                .status(paymentStatus)
                .failureReason(failureReason)
                .build();
    }

    private static Order copyOf(Order originalOrder, OrderStatus orderStatus, long version) {
        return Order.builder()
                .id(originalOrder.getId())
                .userId(originalOrder.getUserId())
                .status(orderStatus)
                .totalAmount(originalOrder.getTotalAmount())
                .currency(originalOrder.getCurrency())
                .items(originalOrder.getItems())
                .version(version)
                .build();
    }

    private InOrder inOrderAll() {
        return inOrder(productServiceClient, orderRepository, paymentServiceClient, outboxService);
    }

    private void verifyNoMoreCalls() {
        verifyNoMoreInteractions(productServiceClient, orderRepository, paymentServiceClient, outboxService);
    }

    private static void assertCancelled(Order finalOrder, String expectedReason) {
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(finalOrder.getFailureReason()).isEqualTo(expectedReason);
    }

}
