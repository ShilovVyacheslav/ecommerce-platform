package com.shilov.ecommerce.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

import static com.shilov.ecommerce.constants.Constants.ACTION_MDC_KEY;
import static com.shilov.ecommerce.constants.Constants.USER_ID_MDC_KEY;

public class RequestContextInterceptor implements HandlerInterceptor {

    private static final boolean JWT_AUTHENTICATION_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
            RequestContextInterceptor.class.getClassLoader());

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            String action = LogActions.http(handlerMethod.getBeanType(), handlerMethod.getMethod().getName());
            request.setAttribute(RequestLogAttributes.ACTION, action);
            MDC.put(ACTION_MDC_KEY, action);
        }
        String userId = JWT_AUTHENTICATION_PRESENT ? JwtUserId.of(request.getUserPrincipal()) : null;
        if (userId != null) {
            request.setAttribute(RequestLogAttributes.USER_ID, userId);
            MDC.put(USER_ID_MDC_KEY, userId);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        MDC.remove(ACTION_MDC_KEY);
        MDC.remove(USER_ID_MDC_KEY);
    }

    private static final class JwtUserId {

        static String of(Principal principal) {
            if (principal instanceof JwtAuthenticationToken authentication) {
                Object id = authentication.getTokenAttributes().get("id");
                return id != null ? id.toString() : null;
            }
            return null;
        }

    }

}
