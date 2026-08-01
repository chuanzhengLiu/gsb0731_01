package com.podcast.collab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+\\.\\d+)");
    private static final int PEAK_BUCKETS = 2000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public long extractDurationMs(Path audioFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                    audioFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String out;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                out = reader.lines().reduce("", (a, b) -> a + b);
            }
            process.waitFor();
            double seconds = Double.parseDouble(out.trim());
            return (long) (seconds * 1000);
        } catch (Exception e) {
            log.warn("ffprobe duration failed: {}", e.getMessage());
            return parseDurationViaFfmpeg(audioFile);
        }
    }

    private long parseDurationViaFfmpeg(Path audioFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", audioFile.toString());
            Process process = pb.start();
            String err;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                err = reader.lines().reduce("", (a, b) -> a + b + "\n");
            }
            process.waitFor();
            Matcher m = DURATION_PATTERN.matcher(err);
            if (m.find()) {
                int h = Integer.parseInt(m.group(1));
                int min = Integer.parseInt(m.group(2));
                double sec = Double.parseDouble(m.group(3));
                return (long) ((h * 3600 + min * 60 + sec) * 1000);
            }
        } catch (Exception e) {
            log.warn("ffmpeg duration fallback failed: {}", e.getMessage());
        }
        throw new ApiException(ErrorCode.BAD_FILE, "Unable to read audio duration; is ffmpeg installed?");
    }

    public Path generatePeaks(Path audioFile, Path peaksFile) throws IOException, InterruptedException {
        Files.createDirectories(peaksFile.getParent());
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats",
                "-i", audioFile.toString(),
                "-ac", "1", "-ar", "8000", "-f", "s16le", "-");
        Process process = pb.start();
        byte[] data = process.getInputStream().readAllBytes();
        process.waitFor();

        List<Double> peaks = bucketPeaks(data, PEAK_BUCKETS);
        objectMapper.writeValue(peaksFile.toFile(), peaks);
        return peaksFile;
    }

    private List<Double> bucketPeaks(byte[] data, int buckets) {
        int samples = data.length / 2;
        int samplesPerBucket = Math.max(1, samples / buckets);
        List<Double> peaks = new ArrayList<>(buckets);
        for (int b = 0; b < buckets; b++) {
            int start = b * samplesPerBucket;
            int end = (b == buckets - 1) ? samples : Math.min(samples, start + samplesPerBucket);
            double peak = 0.0;
            for (int i = start; i < end; i++) {
                int bi = i * 2;
                short sample = (short) ((data[bi + 1] << 8) | (data[bi] & 0xff));
                double v = Math.abs((double) sample) / 32768.0;
                if (v > peak) peak = v;
            }
            peaks.add(peak);
        }
        return peaks;
    }
}
