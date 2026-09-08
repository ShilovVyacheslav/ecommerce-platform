package com.shilov.ecommerce.orderservice.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class ProductLookupExecutorConfig {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 50;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final long AWAIT_TERMINATION_SECONDS = 10L;

    private ThreadPoolExecutor executor;

    @Bean(name = "productLookupExecutor")
    public ExecutorService productLookupExecutor() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory namedThreadFactory = runnable -> {
            Thread thread = new Thread(runnable, "product-lookup-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        this.executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                namedThreadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        return executor;
    }

    @PreDestroy
    public void shutdown() {
        if (executor == null) {
            return;
        }
        log.info("Shutting down productLookupExecutor, awaiting in-flight tasks");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                log.warn("productLookupExecutor did not terminate in {}s, forcing shutdown",
                        AWAIT_TERMINATION_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
