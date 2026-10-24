package com.shilov.ecommerce.orderservice.support.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.shilov.ecommerce.orderservice.support.SagaCallJournal;
import com.shilov.ecommerce.orderservice.support.SagaCallJournal.Step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.shilov.ecommerce.constants.Constants.INTERNAL_VERSION_V1;

/**
 * Feeds every request that reaches the stubbed payment-service into the {@link SagaCallJournal},
 * at the moment it arrives and before any (possibly delayed) response is sent.
 */
public class PaymentServiceCallRecorder implements ServeEventListener {

    public static final String PAYMENTS_PATH = INTERNAL_VERSION_V1 + "/payments";

    private static final Pattern REFUND_PATH = Pattern.compile(PAYMENTS_PATH + "/([0-9a-fA-F-]{36})/refund");

    private final SagaCallJournal journal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentServiceCallRecorder(SagaCallJournal journal) {
        this.journal = journal;
    }

    @Override
    public String getName() {
        return "payment-service-call-recorder";
    }

    @Override
    public void beforeMatch(ServeEvent serveEvent, Parameters parameters) {
        String path = serveEvent.getRequest().getUrl();
        if (path.equals(PAYMENTS_PATH)) {
            journal.record(Step.CHARGE, chargedOrderId(serveEvent.getRequest().getBodyAsString()));
            return;
        }
        Matcher refund = REFUND_PATH.matcher(path);
        if (refund.matches()) {
            journal.record(Step.REFUND, UUID.fromString(refund.group(1)));
        }
    }

    private UUID chargedOrderId(String body) {
        try {
            JsonNode orderId = objectMapper.readTree(body).get("orderId");
            return orderId == null ? null : UUID.fromString(orderId.asText());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
