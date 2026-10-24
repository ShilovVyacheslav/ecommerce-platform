package com.shilov.ecommerce.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ProductLookupExecutorConfig {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 50;
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final int AWAIT_TERMINATION_SECONDS = 10;

    @Bean(name = "productLookupExecutor")
    public ThreadPoolTaskExecutor productLookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("product-lookup-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // keep serving in-flight requests during the web server's graceful shutdown
        executor.setAcceptTasksAfterContextClose(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }

}
