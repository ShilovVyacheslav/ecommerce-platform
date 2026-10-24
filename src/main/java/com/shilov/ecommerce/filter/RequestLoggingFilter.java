package com.shilov.ecommerce.filter;

import com.shilov.ecommerce.logging.RequestLogAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.v;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    static final String ACCESS_LOGGER = "com.shilov.ecommerce.access";

    private static final Logger log = LoggerFactory.getLogger(ACCESS_LOGGER);

    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of(
            "/actuator", "/v3/api-docs", "/swagger-ui", "/.well-known/", "/favicon.ico");
    private static final int MAX_PATH_LENGTH = 256;

    private final Duration slowRequestThreshold;

    public RequestLoggingFilter(@Value("${app.logging.access.slow-request-threshold:2s}") Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
            logRequest(request, status, durationMs);
        }
    }

    private void logRequest(HttpServletRequest request, int status, long durationMs) {
        boolean slow = durationMs >= slowRequestThreshold.toMillis();
        Level level = status >= 500 || slow ? Level.WARN : Level.INFO;
        if (!log.isEnabledForLevel(level)) {
            return;
        }
        LoggingEventBuilder event = log.atLevel(level);
        addIfPresent(event, "httpRoute", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        addIfPresent(event, "action", request.getAttribute(RequestLogAttributes.ACTION));
        addIfPresent(event, "userId", request.getAttribute(RequestLogAttributes.USER_ID));
        addIfPresent(event, "errorCode", request.getAttribute(RequestLogAttributes.ERROR_CODE));
        event.log("{} {} -> {} in {} ms{}",
                v("httpMethod", request.getMethod()),
                v("httpPath", truncate(request.getRequestURI())),
                v("httpStatus", status),
                v("durationMs", durationMs),
                slow ? " (slow request)" : "");
    }

    private static void addIfPresent(LoggingEventBuilder event, String key, Object value) {
        if (value != null) {
            event.addKeyValue(key, value);
        }
    }

    private static String truncate(String path) {
        return path.length() <= MAX_PATH_LENGTH ? path : path.substring(0, MAX_PATH_LENGTH);
    }

}
