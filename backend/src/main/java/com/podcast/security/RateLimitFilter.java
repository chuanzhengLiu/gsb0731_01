package com.podcast.security;

import com.podcast.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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

/**
 * bucket4j-based rate limiting (README §8):
 *  - login  : 5/min
 *  - upload : 10/min
 *  - general: 100/min
 * Keyed per client IP + bucket category. Runs before authentication.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props) {
        this.props = props;
    }

    private Bucket newBucket(int perMinute) {
        Bandwidth limit = Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String category(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String method = req.getMethod();
        // Auth-sensitive endpoints share the strict login bucket (5/min) to
        // throttle credential guessing / token brute-forcing.
        if (uri.equals("/api/auth/login")
                || uri.equals("/api/auth/password/forgot")
                || uri.equals("/api/auth/password/reset")
                || uri.equals("/api/invitations/accept")) {
            return "login";
        }
        if ("POST".equalsIgnoreCase(method) && uri.matches(".*/audio-versions.*")) {
            return "upload";
        }
        return "general";
    }

    private int limitFor(String category) {
        return switch (category) {
            case "login" -> props.getRateLimit().getLoginPerMinute();
            case "upload" -> props.getRateLimit().getUploadPerMinute();
            default -> props.getRateLimit().getGeneralPerMinute();
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String category = category(request);
        String key = clientIp(request) + ":" + category;
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limitFor(category)));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"请求过于频繁，请稍后再试\"}");
        }
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit API traffic
        return !request.getRequestURI().startsWith("/api/");
    }
}
