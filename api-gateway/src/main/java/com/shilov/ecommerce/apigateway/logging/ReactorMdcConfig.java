package com.shilov.ecommerce.apigateway.logging;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Configuration(proxyBeanMethods = false)
public class ReactorMdcConfig {

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CORRELATION_ID_MDC_KEY,
                () -> MDC.get(CORRELATION_ID_MDC_KEY),
                correlationId -> MDC.put(CORRELATION_ID_MDC_KEY, correlationId),
                () -> MDC.remove(CORRELATION_ID_MDC_KEY));
    }

}
