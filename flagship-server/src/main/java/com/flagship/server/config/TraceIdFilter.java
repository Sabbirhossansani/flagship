package com.flagship.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every HTTP request has a correlation Trace ID in MDC and response headers (C4).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String existingMdc = MDC.get(MDC_TRACE_ID_KEY);
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        String traceId = (headerTraceId != null && !headerTraceId.isBlank())
                ? headerTraceId.trim()
                : (existingMdc != null && !existingMdc.isBlank()
                ? existingMdc
                : UUID.randomUUID().toString().replace("-", ""));

        boolean putMdc = (existingMdc == null || existingMdc.isBlank() || (headerTraceId != null && !headerTraceId.isBlank()));
        if (putMdc) {
            MDC.put(MDC_TRACE_ID_KEY, traceId);
        }
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (putMdc) {
                MDC.remove(MDC_TRACE_ID_KEY);
            }
        }
    }
}
