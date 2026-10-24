package com.shilov.ecommerce.apigateway.logging;

import com.shilov.ecommerce.logging.CorrelationIds;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".correlationId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationIds.acceptOrGenerate(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();
        ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();
        correlatedExchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);
        correlatedExchange.getResponse().beforeCommit(() -> {
            correlatedExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
            return Mono.empty();
        });

        return chain.filter(correlatedExchange)
                .contextWrite(Context.of(CORRELATION_ID_MDC_KEY, correlationId));
    }

}
