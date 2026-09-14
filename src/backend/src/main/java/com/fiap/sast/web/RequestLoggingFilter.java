package com.fiap.sast.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        var requestId = UUID.randomUUID().toString();
        var started = System.nanoTime();
        MDC.put(REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            var event = response.getStatus() >= 400 ? "http_request_failed" : "http_request_completed";
            var entry = (response.getStatus() >= 500 ? log.atError() : response.getStatus() >= 400 ? log.atWarn() : log.atInfo())
                    .setMessage(event)
                    .addKeyValue("event", event)
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route(request.getRequestURI()))
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000);
            entry.log();
            MDC.remove(REQUEST_ID);
        }
    }

    private static String route(String uri) {
        if (uri.equals("/api/auth/login")) return "/api/auth/login";
        if (uri.equals("/api/auth/logout")) return "/api/auth/logout";
        if (uri.equals("/api/auth/session")) return "/api/auth/session";
        if (uri.equals("/api/analyses")) return "/api/analyses";
        if (uri.startsWith("/api/analyses/")) return "/api/analyses/{id}";
        return "/api/*";
    }
}
