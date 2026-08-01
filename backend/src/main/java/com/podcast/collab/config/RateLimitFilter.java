package com.podcast.collab.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long CAPACITY = 100_000L;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final Bandwidth genericLimit = Bandwidth.builder()
            .capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build();
    private final Bandwidth loginLimit = Bandwidth.builder()
            .capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build();
    private final Bandwidth uploadLimit = Bandwidth.builder()
            .capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> buildBucket(request));
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
        }
        if (buckets.size() > CAPACITY) {
            buckets.clear();
        }
    }

    private Bucket buildBucket(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.endsWith("/auth/login")) {
            return Bucket.builder().addLimit(loginLimit).build();
        }
        if (uri.contains("/audio/") && "POST".equalsIgnoreCase(request.getMethod())) {
            return Bucket.builder().addLimit(uploadLimit).addLimit(genericLimit).build();
        }
        return Bucket.builder().addLimit(genericLimit).build();
    }

    private String resolveKey(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip + ":" + request.getRequestURI();
    }
}
