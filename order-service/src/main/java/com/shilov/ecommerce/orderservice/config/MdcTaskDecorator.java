package com.shilov.ecommerce.orderservice.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Runs each task with the MDC of the thread that submitted it (correlation id included), so that
 * outbound clients and logs on pool threads see the same context as the request thread.
 * <p>
 * The worker's own MDC is restored afterwards, which also keeps the caller intact when a task is run
 * on the submitting thread (e.g. by {@code CallerRunsPolicy}).
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            setContext(submitterContext);
            try {
                runnable.run();
            } finally {
                setContext(workerContext);
            }
        };
    }

    private static void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

}
