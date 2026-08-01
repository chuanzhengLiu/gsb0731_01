package com.podcast.service;

import com.podcast.config.AppProperties;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Issues and verifies HMAC-signed, short-lived audio streaming tokens
 * (README §8: 音频 URL 带签名 token，过期失效).
 * Token format: base64url(payload) . base64url(hmac) where
 * payload = "{audioVersionId}:{expiresEpochSec}".
 */
@Service
public class SignedUrlService {

    private final byte[] secret;
    private final long ttlSeconds;

    public SignedUrlService(AppProperties props) {
        this.secret = props.getMedia().getUrlSigningSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = props.getMedia().getUrlTtlSeconds();
    }

    public String sign(Long audioVersionId) {
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payload = audioVersionId + ":" + exp;
        String sig = hmac(payload);
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + sig;
    }

    /** Signs a token for a different resource kind (e.g. "asset") to keep
     *  asset preview URLs distinct from audio-version streaming tokens. */
    public String sign(String kind, Long resourceId) {
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payload = kind + ":" + resourceId + ":" + exp;
        String sig = hmac(payload);
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + sig;
    }

    /** Verifies a kinded token and returns the resource id, or throws. */
    public Long verify(String kind, String token) {
        String payload = decodeAndCheck(token);
        String[] fields = payload.split(":");
        if (fields.length != 3 || !kind.equals(fields[0])) {
            throw ApiException.unauthorized("访问令牌格式错误");
        }
        long exp = Long.parseLong(fields[2]);
        if (exp < System.currentTimeMillis() / 1000) {
            throw ApiException.unauthorized("访问令牌已过期");
        }
        return Long.parseLong(fields[1]);
    }

    /** Decodes a token, verifies its signature, and returns the raw payload. */
    private String decodeAndCheck(String token) {
        if (token == null || !token.contains(".")) {
            throw ApiException.unauthorized("访问令牌无效");
        }
        String[] parts = token.split("\\.", 2);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("访问令牌无效");
        }
        String expectedSig = hmac(payload);
        if (!constantTimeEquals(expectedSig, parts[1])) {
            throw ApiException.unauthorized("访问令牌签名不匹配");
        }
        return payload;
    }

    /** Verifies token and returns the audioVersionId, or throws if invalid/expired. */
    public Long verify(String token) {
        String payload = decodeAndCheck(token);
        String[] fields = payload.split(":");
        if (fields.length != 2) {
            throw ApiException.unauthorized("音频访问令牌格式错误");
        }
        long exp = Long.parseLong(fields[1]);
        if (exp < System.currentTimeMillis() / 1000) {
            throw ApiException.unauthorized("音频访问令牌已过期");
        }
        return Long.parseLong(fields[0]);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
