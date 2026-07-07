package com.smart.parking.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter: 100 requests per IP per minute.
 * Handles burst traffic (100 users → 100 users → 100 users) without crashing.
 * Returns 429 Too Many Requests when limit exceeded.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_MS = 60_000; // 1 minute

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        // Skip rate limiting for H2 console
        String path = httpReq.getRequestURI();
        if (path.startsWith("/h2-console")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpReq);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket());

        if (bucket.tryConsume()) {
            // Add rate limit headers
            httpRes.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            httpRes.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining()));
            chain.doFilter(request, response);
        } else {
            log.warn("🚫 RATE LIMIT EXCEEDED → IP: {}, Path: {}", clientIp, path);
            httpRes.setStatus(429);
            httpRes.setContentType("application/json");
            httpRes.setHeader("Retry-After", "60");
            httpRes.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            httpRes.setHeader("X-RateLimit-Remaining", "0");
            httpRes.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Max " 
                    + MAX_REQUESTS_PER_MINUTE + " requests per minute. Please retry after 60 seconds.\",\"status\":429}");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple token bucket implementation.
     * Refills to MAX_REQUESTS_PER_MINUTE every WINDOW_MS.
     */
    private static class TokenBucket {
        private final AtomicInteger tokens = new AtomicInteger(MAX_REQUESTS_PER_MINUTE);
        private final AtomicLong lastRefill = new AtomicLong(System.currentTimeMillis());

        boolean tryConsume() {
            refillIfNeeded();
            return tokens.getAndDecrement() > 0;
        }

        int getRemaining() {
            refillIfNeeded();
            return Math.max(0, tokens.get());
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            long last = lastRefill.get();
            if (now - last >= WINDOW_MS) {
                if (lastRefill.compareAndSet(last, now)) {
                    tokens.set(MAX_REQUESTS_PER_MINUTE);
                }
            }
        }
    }
}
