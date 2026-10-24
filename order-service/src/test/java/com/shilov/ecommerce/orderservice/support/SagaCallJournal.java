package com.shilov.ecommerce.orderservice.support;

import com.shilov.ecommerce.orderservice.enums.OrderStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Cross-service, ordered record of every saga call that reached the fake product-service (gRPC)
 * or the stubbed payment-service (REST). It is the integration-level counterpart of Mockito's {@code InOrder}.
 * <p>
 * Each entry also captures the order status that was <b>committed in the database at the moment
 * the remote call arrived</b>, which proves that the saga persists its progress before every next step.
 */
public final class SagaCallJournal {

    public enum Step { RESERVE, CHARGE, CONFIRM, RELEASE, REFUND }

    public record Call(Step step, OrderStatus persistedStatus) {}

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    private volatile Function<UUID, OrderStatus> persistedStatusProbe = orderId -> null;

    public static Call call(Step step, OrderStatus persistedStatus) {
        return new Call(step, persistedStatus);
    }

    public void usePersistedStatusProbe(Function<UUID, OrderStatus> probe) {
        this.persistedStatusProbe = probe;
    }

    public void record(Step step, UUID orderId) {
        calls.add(new Call(step, persistedStatusProbe.apply(orderId)));
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Step> steps() {
        return calls.stream().map(Call::step).toList();
    }

    public long count(Step step) {
        return calls.stream().filter(call -> call.step() == step).count();
    }

    public void clear() {
        calls.clear();
    }

}
