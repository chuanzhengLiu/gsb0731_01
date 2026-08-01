package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedUrlServiceTest {

    private SignedUrlService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("test-secret-key-that-is-at-least-32-bytes-long!!");
        service = new SignedUrlService(props);
    }

    @Test
    void validStreamUrlVerifies() {
        String path = "/api/files/audio/abc123.mp3";
        String signed = service.signStream(path);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        String query = signed.substring(signed.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[1]);
        }

        var verification = service.verify(req);
        assertThat(verification.path()).isEqualTo(path);
        assertThat(verification.download()).isFalse();
    }

    @Test
    void downloadUrlMarkedAsAttachment() {
        String path = "/api/files/audio/abc123.mp3";
        String signed = service.signDownload(path);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        for (String pair : signed.substring(signed.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[1]);
        }

        var verification = service.verify(req);
        assertThat(verification.download()).isTrue();
    }

    @Test
    void missingSignatureIsForbidden() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/files/audio/abc.mp3");
        assertThatThrownBy(() -> service.verify(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void tamperedSignatureIsForbidden() {
        String path = "/api/files/audio/abc.mp3";
        String signed = service.signStream(path);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        for (String pair : signed.substring(signed.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[0].equals("sig") ? "deadbeef" : kv[1]);
        }
        assertThatThrownBy(() -> service.verify(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void expiredUrlIsRejected() {
        String path = "/api/files/audio/abc.mp3";
        long exp = Instant.now().minus(Duration.ofMinutes(1)).getEpochSecond();
        String sig = hmac(service, path + "|" + exp + "|0");
        String signed = path + "?exp=" + exp + "&sig=" + sig;

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        for (String pair : signed.substring(signed.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[1]);
        }
        assertThatThrownBy(() -> service.verify(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void rssEnclosureVerifies() {
        String absolute = "http://localhost:8080/api/files/audio/final.mp3";
        String signed = service.signRssEnclosure(absolute);

        String query = signed.substring(signed.indexOf('?') + 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/files/audio/final.mp3");
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[1]);
        }

        var verification = service.verify(req);
        assertThat(verification.download()).isFalse();
        assertThat(signed).startsWith(absolute);
    }

    @Test
    void signAndVerifyAgreeOnContextPath() {
        // This is the exact bug from round 2: sign must use the same path verify sees.
        String path = "/api/files/peaks/xyz.peaks.json";
        String signed = service.signStream(path);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        for (String pair : signed.substring(signed.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            req.addParameter(kv[0], kv[1]);
        }
        assertThat(service.verify(req).path()).isEqualTo(path);
    }

    private static String hmac(SignedUrlService service, String data) {
        try {
            var field = SignedUrlService.class.getDeclaredField("signingKey");
            field.setAccessible(true);
            byte[] key = (byte[]) field.get(service);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
