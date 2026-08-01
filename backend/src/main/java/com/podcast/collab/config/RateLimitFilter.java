package com.podcast.collab.config;

import com.podcast.collab.security.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bucket4j 接口限流：通用 100次/分钟，登录 5次/分钟，上传 10次/分钟
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int generalPerMinute;
    private final int loginPerMinute;
    private final int registerPerMinute;
    private final int uploadPerMinute;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(@Value("${app.rate-limit.general-per-minute}") int generalPerMinute,
                           @Value("${app.rate-limit.login-per-minute}") int loginPerMinute,
                           @Value("${app.rate-limit.register-per-minute}") int registerPerMinute,
                           @Value("${app.rate-limit.upload-per-minute}") int uploadPerMinute,
                           ClientIpResolver clientIpResolver) {
        this.generalPerMinute = generalPerMinute;
        this.loginPerMinute = loginPerMinute;
        this.registerPerMinute = registerPerMinute;
        this.uploadPerMinute = uploadPerMinute;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = generalPerMinute;
        String category = "general";
        if (path.startsWith("/api/auth/login")) {
            limit = loginPerMinute;
            category = "login";
        } else if (path.startsWith("/api/auth/register")) {
            limit = registerPerMinute; // 注册独立桶，不与登录共享额度
            category = "register";
        } else if (path.contains("/audio") && "POST".equalsIgnoreCase(request.getMethod())) {
            limit = uploadPerMinute;
            category = "upload";
        }
        String key = category + ":" + clientIpResolver.resolve(request);
        final int limitFinal = limit;
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limitFinal));
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"请求过于频繁，请稍后再试\"}");
        }
    }

    private Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1))))
                .build();
    }
}
