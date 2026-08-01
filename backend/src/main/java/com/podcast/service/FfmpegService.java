package com.podcast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wraps FFmpeg/ffprobe for audio metadata extraction and waveform peak
 * pre-generation (README §5, §9). Peaks are stored as JSON so wavesurfer.js
 * can render a 1-hour waveform within the 5s budget without client decoding.
 */
@Service
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /** Number of waveform buckets (peaks) to pre-compute. */
    private static final int PEAK_BUCKETS = 1000;

    /** Extracted metadata. */
    public record AudioMeta(Long durationMs) {}

    public AudioMeta probe(Path file) {
        try {
            List<String> cmd = List.of(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toString());
            String out = runCapture(cmd, 30);
            if (out != null && !out.isBlank()) {
                double seconds = Double.parseDouble(out.trim());
                return new AudioMeta(Math.round(seconds * 1000));
            }
        } catch (Exception e) {
            log.warn("ffprobe failed for {}: {}", file, e.getMessage());
        }
        return new AudioMeta(null);
    }

    /**
     * Generates normalized waveform peaks JSON:
     * { "version": 1, "channels": 1, "sampleRate": <buckets>, "peaks": [ ... ] }
     * Peaks are absolute amplitude values in [0,1], one per bucket.
     * Returns null if FFmpeg is unavailable (waveform then rendered client-side).
     */
    public String generateWaveformJson(Path file, Long durationMs) {
        try {
            // Decode to 8kHz mono signed 16-bit PCM for lightweight peak extraction.
            int sampleRate = 8000;
            List<String> cmd = List.of(
                    "ffmpeg", "-v", "error", "-i", file.toString(),
                    "-ac", "1", "-ar", String.valueOf(sampleRate),
                    "-f", "s16le", "-");
            byte[] pcm = runCaptureBytes(cmd, 120);
            if (pcm == null || pcm.length < 2) {
                return null;
            }

            int totalSamples = pcm.length / 2;
            int bucketSize = Math.max(1, totalSamples / PEAK_BUCKETS);
            List<Double> peaks = new ArrayList<>();
            int i = 0;
            while (i < totalSamples) {
                int max = 0;
                int end = Math.min(i + bucketSize, totalSamples);
                for (int s = i; s < end; s++) {
                    int lo = pcm[s * 2] & 0xFF;
                    int hi = pcm[s * 2 + 1];
                    int sample = (hi << 8) | lo; // little-endian signed 16-bit
                    int abs = Math.abs(sample);
                    if (abs > max) max = abs;
                }
                peaks.add(Math.min(1.0, max / 32768.0));
                i = end;
            }

            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("version", 1);
            payload.put("channels", 1);
            payload.put("buckets", peaks.size());
            payload.put("durationMs", durationMs);
            payload.put("peaks", peaks);
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("waveform generation failed for {}: {}", file, e.getMessage());
            return null;
        }
    }

    private String runCapture(List<String> cmd, int timeoutSeconds) throws Exception {
        byte[] bytes = runCaptureBytes(cmd, timeoutSeconds);
        return bytes == null ? null : new String(bytes);
    }

    private byte[] runCaptureBytes(List<String> cmd, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
        }
        boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new RuntimeException("命令超时: " + cmd.get(0));
        }
        if (proc.exitValue() != 0) {
            throw new RuntimeException(cmd.get(0) + " 退出码 " + proc.exitValue());
        }
        return buffer.toByteArray();
    }
}
