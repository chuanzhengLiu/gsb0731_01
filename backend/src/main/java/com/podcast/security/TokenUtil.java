package com.podcast.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared helpers for opaque security tokens (refresh tokens, invitation
 * links, password-reset links). Raw tokens are returned to the client once;
 * only their SHA-256 hash is persisted, matching the refresh_token design.
 */
public final class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtil() {}

    /** Generates a URL-safe random token (~64 chars). */
    public static String generateRawToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
