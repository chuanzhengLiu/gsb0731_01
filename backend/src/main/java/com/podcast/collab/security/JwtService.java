package com.podcast.collab.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtService {
    private final Key key;
    private final long accessTtlMillis;
    private final String rawSecret;

    public JwtService(@Value("${app.jwt-secret}") String secret,
                      @Value("${app.access-token-ttl-minutes}") long accessTtlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // jjwt 要求 HS256 密钥至少 256bit，不足则填充
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlMillis = accessTtlMinutes * 60_000;
        this.rawSecret = secret;
    }

    public String generateAccessToken(Long userId, Long teamId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("teamId", teamId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTtlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }

    /** 音频签名 URL：HMAC(versionId:expiry)，过期失效 */
    public String signAudioUrl(Long versionId, long expiryEpochSec) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((versionId + ":" + expiryEpochSec).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成音频签名", e);
        }
    }

    public boolean verifyAudioSignature(Long versionId, long expiryEpochSec, String signature) {
        if (expiryEpochSec < System.currentTimeMillis() / 1000) {
            return false;
        }
        String expected = signAudioUrl(versionId, expiryEpochSec);
        return constantTimeEquals(expected, signature);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
