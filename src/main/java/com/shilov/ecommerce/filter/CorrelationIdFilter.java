package com.shilov.ecommerce.filter;

import com.shilov.ecommerce.logging.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_HEADER;
import static com.shilov.ecommerce.constants.Constants.CORRELATION_ID_MDC_KEY;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = CorrelationIds.acceptOrGenerate(request.getHeader(CORRELATION_ID_HEADER));
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

}
