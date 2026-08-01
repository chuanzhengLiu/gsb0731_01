package com.podcast.collab.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户端 IP 解析：X-Forwarded-For 可被客户端任意伪造，
 * 仅当直连对端（remoteAddr）命中配置的可信代理时才采信 XFF。
 */
@Component
public class ClientIpResolver {
    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${app.trusted-proxies:}") String trustedProxiesConfig) {
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr; // 对端不是可信代理：XFF 不可信
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        // XFF 形如 "client, proxy1, proxy2"：最左端可被客户端伪造。
        // 从右往左跳过可信代理，取第一个不可信地址作为真实客户端 IP。
        String[] hops = xff.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }
}
