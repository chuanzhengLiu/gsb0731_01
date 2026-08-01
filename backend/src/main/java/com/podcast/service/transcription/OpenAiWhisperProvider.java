package com.podcast.service.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Whisper API provider (README §4.3, §5). The API key is read from
 * configuration (populated from an environment variable) — never hardcoded.
 * Requests the verbose_json response so we get per-segment timestamps.
 *
 * OpenAI's transcription API does not return speaker diarization, so speaker
 * labels are left null here and assigned heuristically downstream.
 */
@Component
public class OpenAiWhisperProvider implements TranscriptionProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWhisperProvider.class);

    private final AppProperties.Transcription cfg;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiWhisperProvider(AppProperties props) {
        this.cfg = props.getTranscription();
    }

    @Override
    public String name() {
        return "openai-whisper(" + cfg.getModel() + ")";
    }

    @Override
    public boolean isAvailable() {
        String key = cfg.getApiKey();
        boolean hasKey = key != null && !key.isBlank();
        String provider = cfg.getProvider();
        // Usable when explicitly selected, or in auto mode with a key present.
        return hasKey && ("openai".equalsIgnoreCase(provider) || "auto".equalsIgnoreCase(provider));
    }

    @Override
    public List<RawSegment> transcribe(Path audioFile, Long durationMs) {
        try {
            String boundary = "----podcast" + System.nanoTime();
            byte[] body = multipartBody(boundary, audioFile);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getApiBaseUrl() + "/audio/transcriptions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Whisper API returned {}: {}", resp.statusCode(), resp.body());
                return List.of();
            }
            return parse(resp.body());
        } catch (Exception e) {
            // Network / auth failure should not break the upload flow.
            log.warn("Whisper API transcription failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RawSegment> parse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        List<RawSegment> out = new ArrayList<>();
        JsonNode segments = root.get("segments");
        if (segments != null && segments.isArray()) {
            for (JsonNode s : segments) {
                long start = Math.round(s.path("start").asDouble() * 1000);
                long end = Math.round(s.path("end").asDouble() * 1000);
                String text = s.path("text").asText("").trim();
                if (!text.isEmpty()) {
                    out.add(new RawSegment(start, end, text, null));
                }
            }
        } else if (root.hasNonNull("text")) {
            String text = root.get("text").asText().trim();
            if (!text.isEmpty()) {
                out.add(new RawSegment(0, 0, text, null));
            }
        }
        return out;
    }

    private byte[] multipartBody(String boundary, Path file) throws Exception {
        var baos = new java.io.ByteArrayOutputStream();
        String dd = "--";
        String crlf = "\r\n";

        // model field
        baos.write((dd + boundary + crlf).getBytes());
        baos.write(("Content-Disposition: form-data; name=\"model\"" + crlf + crlf).getBytes());
        baos.write((cfg.getModel() + crlf).getBytes());

        // response_format = verbose_json (per-segment timestamps)
        baos.write((dd + boundary + crlf).getBytes());
        baos.write(("Content-Disposition: form-data; name=\"response_format\"" + crlf + crlf).getBytes());
        baos.write(("verbose_json" + crlf).getBytes());

        // language
        baos.write((dd + boundary + crlf).getBytes());
        baos.write(("Content-Disposition: form-data; name=\"language\"" + crlf + crlf).getBytes());
        baos.write((cfg.getLanguage() + crlf).getBytes());

        // file field
        baos.write((dd + boundary + crlf).getBytes());
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + file.getFileName() + "\"" + crlf).getBytes());
        baos.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes());
        baos.write(Files.readAllBytes(file));
        baos.write(crlf.getBytes());

        baos.write((dd + boundary + dd + crlf).getBytes());
        return baos.toByteArray();
    }
}
