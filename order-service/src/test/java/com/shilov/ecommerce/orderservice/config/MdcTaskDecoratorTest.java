package com.shilov.ecommerce.orderservice.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MdcTaskDecorator")
public class MdcTaskDecoratorTest {

    private static final String SUBMITTER_CORRELATION_ID = "submitter-correlation-id";
    private static final String WORKER_CORRELATION_ID = "worker-correlation-id";

    private final MdcTaskDecorator mdcTaskDecorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Task on another thread -> sees the submitter's MDC, the worker thread is clean afterwards")
    void decorate_TaskOnAnotherThread_PropagatesSubmitterMdc() throws Exception {
        MDC.put(CORRELATION_ID_MDC_KEY, SUBMITTER_CORRELATION_ID);
        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        Runnable task = mdcTaskDecorator.decorate(() -> seenCorrelationId.set(MDC.get(CORRELATION_ID_MDC_KEY)));

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(task).get();
            Map<String, String> workerMdcAfterTask = worker.submit(MDC::getCopyOfContextMap).get();

            assertThat(seenCorrelationId).hasValue(SUBMITTER_CORRELATION_ID);
            assertThat(workerMdcAfterTask).isNullOrEmpty();
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    @DisplayName("MDC is captured at submission -> later changes on the submitter do not leak into the task")
    void decorate_SubmitterMdcChangesAfterSubmission_TaskSeesSnapshot() {
        MDC.put(CORRELATION_ID_MDC_KEY, SUBMITTER_CORRELATION_ID);
        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        Runnable task = mdcTaskDecorator.decorate(() -> seenCorrelationId.set(MDC.get(CORRELATION_ID_MDC_KEY)));
        MDC.put(CORRELATION_ID_MDC_KEY, "changed-after-submission");

        CompletableFuture.runAsync(task).join();

        assertThat(seenCorrelationId).hasValue(SUBMITTER_CORRELATION_ID);
    }

    @Test
    @DisplayName("Run on a thread with its own MDC (caller-runs) -> that MDC is restored afterwards")
    void decorate_ThreadHasOwnMdc_RestoresItAfterTask() {
        MDC.put(CORRELATION_ID_MDC_KEY, SUBMITTER_CORRELATION_ID);
        Runnable task = mdcTaskDecorator.decorate(() -> MDC.put("taskOnly", "value"));
        MDC.put(CORRELATION_ID_MDC_KEY, WORKER_CORRELATION_ID);

        task.run();

        assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry(CORRELATION_ID_MDC_KEY, WORKER_CORRELATION_ID));
    }

    @Test
    @DisplayName("Task throws -> exception propagates and the thread's MDC is still restored")
    void decorate_TaskThrows_RestoresMdc() {
        MDC.put(CORRELATION_ID_MDC_KEY, SUBMITTER_CORRELATION_ID);
        IllegalStateException failure = new IllegalStateException("task failed");
        Runnable task = mdcTaskDecorator.decorate(() -> {
            throw failure;
        });
        MDC.put(CORRELATION_ID_MDC_KEY, WORKER_CORRELATION_ID);

        assertThatThrownBy(task::run).isSameAs(failure);

        assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isEqualTo(WORKER_CORRELATION_ID);
    }

    @Test
    @DisplayName("Submitter has no MDC -> task runs with an empty MDC, nothing stale from the worker")
    void decorate_SubmitterWithoutMdc_TaskRunsWithEmptyMdc() {
        Runnable task = mdcTaskDecorator.decorate(() -> assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty());
        MDC.put(CORRELATION_ID_MDC_KEY, WORKER_CORRELATION_ID);

        task.run();

        assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isEqualTo(WORKER_CORRELATION_ID);
    }

}
