package com.podcast.collab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.dto.common.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitingFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> uploadBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    private final int loginLimit;
    private final int uploadLimit;
    private final int generalLimit;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(
            @Value("${app.rate-limit.login:5}") int loginLimit,
            @Value("${app.rate-limit.upload:10}") int uploadLimit,
            @Value("${app.rate-limit.general:100}") int generalLimit,
            ObjectMapper objectMapper) {
        this.loginLimit = loginLimit;
        this.uploadLimit = uploadLimit;
        this.generalLimit = generalLimit;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        String clientIp = resolveClientIp(request);
        Bucket bucket;

        if (isLoginRequest(path, request.getMethod())) {
            bucket = loginBuckets.computeIfAbsent(clientIp, this::createLoginBucket);
        } else if (isUploadRequest(path, request.getMethod())) {
            bucket = uploadBuckets.computeIfAbsent(clientIp, this::createUploadBucket);
        } else if (path.startsWith("/share/token/") && "GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        } else {
            bucket = generalBuckets.computeIfAbsent(clientIp, this::createGeneralBucket);
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeRateLimitResponse(response);
        }
    }

    private boolean isLoginRequest(String path, String method) {
        return "POST".equalsIgnoreCase(method) && path.equals("/auth/login");
    }

    private boolean isUploadRequest(String path, String method) {
        return "POST".equalsIgnoreCase(method) && path.startsWith("/audio/upload");
    }

    private Bucket createLoginBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(loginLimit)
                .refillGreedy(loginLimit, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createUploadBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(uploadLimit)
                .refillGreedy(uploadLimit, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createGeneralBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(generalLimit)
                .refillGreedy(generalLimit, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        ApiResponse<Void> body = ApiResponse.error("请求过于频繁，请稍后再试（Rate limit exceeded）");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
