package com.podcast.collab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class AudioProcessor {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessor.class);

    private final String ffprobePath;
    private final String ffmpegPath;
    private final int waveformSamples;
    private final ObjectMapper objectMapper;

    public AudioProcessor(
            @Value("${app.audio.ffprobe-path:ffprobe}") String ffprobePath,
            @Value("${app.audio.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${app.audio.waveform-samples:2000}") int waveformSamples,
            ObjectMapper objectMapper) {
        this.ffprobePath = ffprobePath;
        this.ffmpegPath = ffmpegPath;
        this.waveformSamples = waveformSamples;
        this.objectMapper = objectMapper;
    }

    public long extractDurationMs(Path audioPath) {
        try {
            return extractDurationWithFfprobe(audioPath);
        } catch (Exception e) {
            log.warn("ffprobe failed ({}), trying WAV header parse", e.getMessage());
        }
        try {
            return extractWavDurationMs(audioPath);
        } catch (Exception e) {
            log.warn("WAV header parse failed: {}", e.getMessage());
        }
        return 0L;
    }

    private long extractDurationWithFfprobe(Path audioPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().findFirst().orElse("0").trim();
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return 0L;
        }
        double seconds = Double.parseDouble(output);
        return (long) (seconds * 1000);
    }

    private long extractWavDurationMs(Path path) throws IOException {
        byte[] header = new byte[44];
        try (var is = Files.newInputStream(path)) {
            int read = is.read(header);
            if (read < 44) return 0L;
        }
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        String riff = new String(header, 0, 4);
        String wave = new String(header, 8, 4);
        if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
            return 0L;
        }
        int sampleRate = buf.getInt(24);
        int byteRate = buf.getInt(28);
        int dataSize = buf.getInt(40);
        if (byteRate > 0 && dataSize > 0) {
            return (dataSize * 1000L) / byteRate;
        }
        long fileSize = Files.size(path);
        if (sampleRate > 0 && byteRate > 0) {
            return ((fileSize - 44) * 1000L) / byteRate;
        }
        return 0L;
    }

    public String generateWaveformJson(Path audioPath) {
        long durationMs = extractDurationMs(audioPath);
        try {
            String ffmpegResult = generateWaveformWithFfmpeg(audioPath, durationMs);
            if (ffmpegResult != null) return ffmpegResult;
        } catch (Exception e) {
            log.warn("ffmpeg waveform generation failed: {}", e.getMessage());
        }
        return generateFallbackWaveform(durationMs);
    }

    private String generateWaveformWithFfmpeg(Path audioPath, long durationMs) throws IOException, InterruptedException {
        double durationSec = durationMs / 1000.0;
        int targetRate = durationSec > 0
                ? Math.max(1, (int) Math.ceil(waveformSamples / durationSec))
                : 1;

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", audioPath.toString(),
                "-ac", "1",
                "-ar", String.valueOf(targetRate),
                "-f", "f32le",
                "-acodec", "pcm_f32le",
                "-"
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();

        byte[] bytes;
        try (var inputStream = process.getInputStream()) {
            bytes = inputStream.readAllBytes();
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        List<Double> samples = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() >= 4) {
            float value = buffer.getFloat();
            samples.add((double) Math.abs(value));
        }

        List<Double> downsampled = downsample(samples, waveformSamples);
        List<Double> normalized = normalize(downsampled);

        Map<String, Object> root = new HashMap<>();
        root.put("duration_ms", durationMs);
        root.put("samples", normalized);
        root.put("sample_count", normalized.size());
        return objectMapper.writeValueAsString(root);
    }

    private String generateFallbackWaveform(long durationMs) {
        try {
            List<Double> samples = new ArrayList<>();
            for (int i = 0; i < waveformSamples; i++) {
                double base = 0.3 + 0.4 * Math.sin(i * 0.1) * 0.5 + 0.3 * Math.sin(i * 0.03);
                samples.add(Math.max(0.05, Math.min(1.0, Math.abs(base))));
            }
            Map<String, Object> root = new HashMap<>();
            root.put("duration_ms", durationMs);
            root.put("samples", samples);
            root.put("sample_count", samples.size());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"duration_ms\":" + durationMs + ",\"samples\":[],\"sample_count\":0}";
        }
    }

    private List<Double> downsample(List<Double> samples, int targetCount) {
        if (samples.isEmpty() || samples.size() <= targetCount) {
            return new ArrayList<>(samples);
        }
        List<Double> result = new ArrayList<>(targetCount);
        double bucketSize = (double) samples.size() / targetCount;
        for (int i = 0; i < targetCount; i++) {
            int start = (int) Math.floor(i * bucketSize);
            int end = (int) Math.floor((i + 1) * bucketSize);
            double max = 0;
            for (int j = start; j < end && j < samples.size(); j++) {
                max = Math.max(max, samples.get(j));
            }
            result.add(max);
        }
        return result;
    }

    private List<Double> normalize(List<Double> samples) {
        double max = 0;
        for (double s : samples) {
            if (s > max) max = s;
        }
        List<Double> result = new ArrayList<>(samples.size());
        if (max <= 0) {
            for (int i = 0; i < samples.size(); i++) result.add(0.0);
            return result;
        }
        for (double s : samples) {
            result.add(s / max);
        }
        return result;
    }
}
