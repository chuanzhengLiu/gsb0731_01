package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Signs file URLs with a short-lived HMAC token. The canonical string that is
 * signed and verified is ALWAYS the full request path including the context path
 * (e.g. "/api/files/audio/abc.mp3"), so sign and verify must agree exactly.
 */
@Service
public class SignedUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);
    private static final Duration RSS_TTL = Duration.ofDays(30);

    private final byte[] signingKey;

    public SignedUrlService(AppProperties props) {
        this.signingKey = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** Sign a relative public path (must start with the context path, e.g. "/api/..."). */
    public String signStream(String publicPath) {
        return sign(publicPath, Instant.now().plus(DEFAULT_TTL), false);
    }

    public String signDownload(String publicPath) {
        return sign(publicPath, Instant.now().plus(DEFAULT_TTL), true);
    }

    /**
     * Sign an absolute enclosure URL for RSS feeds. Only the path portion is
     * signed, which matches what verify() sees as requestURI.
     */
    public String signRssEnclosure(String absoluteUrl) {
        java.net.URI uri = java.net.URI.create(absoluteUrl);
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Invalid enclosure URL: " + absoluteUrl);
        }
        return absoluteUrl + buildQuery(path, Instant.now().plus(RSS_TTL), false);
    }

    private String sign(String publicPath, Instant expiresAt, boolean download) {
        return publicPath + buildQuery(publicPath, expiresAt, download);
    }

    private String buildQuery(String path, Instant expiresAt, boolean download) {
        long exp = expiresAt.getEpochSecond();
        String dl = download ? "1" : "0";
        String sig = hmac(path + "|" + exp + "|" + dl);
        StringBuilder q = new StringBuilder()
                .append(path.contains("?") ? "&" : "?")
                .append("exp=").append(exp)
                .append("&sig=").append(urlEncode(sig));
        if (download) q.append("&dl=1");
        return q.toString();
    }

    public Verification verify(HttpServletRequest request) {
        // Use the full request URI (includes context path /api) so it matches the signed path.
        String path = request.getRequestURI();
        String expParam = request.getParameter("exp");
        String sigParam = request.getParameter("sig");
        String dlParam = request.getParameter("dl");
        if (expParam == null || sigParam == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Missing signature");
        }
        long exp;
        try {
            exp = Long.parseLong(expParam);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Invalid signature");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Signed URL has expired");
        }
        boolean download = "1".equals(dlParam);
        String expected = hmac(path + "|" + exp + "|" + (download ? "1" : "0"));
        if (!constantTimeEquals(expected, sigParam)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Invalid signature");
        }
        return new Verification(path, download);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
        return diff == 0;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Verification(String path, boolean download) {}
}
