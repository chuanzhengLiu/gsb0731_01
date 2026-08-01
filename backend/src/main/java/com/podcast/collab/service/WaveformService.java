package com.podcast.collab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 音频元数据与波形：优先使用 FFmpeg（ffprobe/ffmpeg）；
 * 无 FFmpeg 时降级（MP3 返回空波形，WAV 直接解析 PCM）。
 */
@Slf4j
@Service
public class WaveformService {
    private static final int PEAKS_COUNT = 1000;

    public record AudioMeta(Long durationMs, Integer sampleRate) {
    }

    private volatile Boolean ffmpegAvailableCache;

    public boolean ffmpegAvailable() {
        if (ffmpegAvailableCache == null) {
            ffmpegAvailableCache = commandWorks("ffprobe", "-version");
        }
        return ffmpegAvailableCache;
    }

    private boolean commandWorks(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 提取音频元数据 */
    public AudioMeta extractMeta(Path file) {
        if (ffmpegAvailable()) {
            try {
                Process p = new ProcessBuilder("ffprobe", "-v", "quiet", "-show_entries",
                        "format=duration:stream=sample_rate", "-of", "csv=p=0", file.toString())
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor(30, TimeUnit.SECONDS);
                // 输出形如 "44100,3661.234"（顺序可能不同，取含小数点为时长）
                Long durationMs = null;
                Integer sampleRate = null;
                for (String line : out.split("\n")) {
                    for (String part : line.split(",")) {
                        part = part.trim();
                        if (part.contains(".")) {
                            durationMs = Math.round(Double.parseDouble(part) * 1000);
                        } else if (!part.isEmpty() && part.matches("\\d+")) {
                            sampleRate = Integer.parseInt(part);
                        }
                    }
                }
                return new AudioMeta(durationMs, sampleRate);
            } catch (Exception e) {
                log.warn("ffprobe 提取元数据失败: {}", e.getMessage());
            }
        }
        // 降级：WAV 直接解析文件头获取时长/采样率
        if (file.toString().toLowerCase().endsWith(".wav")) {
            return wavMeta(file);
        }
        return new AudioMeta(null, null);
    }

    private AudioMeta wavMeta(Path file) {
        try {
            byte[] head = new byte[65536];
            int read;
            try (InputStream in = java.nio.file.Files.newInputStream(file)) {
                read = in.readNBytes(head, 0, head.length);
            }
            if (read < 44) {
                return new AudioMeta(null, null);
            }
            ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            int sampleRate = bb.getInt(24);
            int byteRate = bb.getInt(28);
            // 从 36 字节处查找 "data" chunk；找不到则用文件总大小估算
            long dataSize = java.nio.file.Files.size(file) - 44;
            for (int i = 36; i < read - 8; i++) {
                if (head[i] == 'd' && head[i + 1] == 'a' && head[i + 2] == 't' && head[i + 3] == 'a') {
                    dataSize = Integer.toUnsignedLong(bb.getInt(i + 4));
                    break;
                }
            }
            Long durationMs = byteRate > 0 ? Math.round(dataSize * 1000.0 / byteRate) : null;
            return new AudioMeta(durationMs, sampleRate > 0 ? sampleRate : null);
        } catch (Exception e) {
            return new AudioMeta(null, null);
        }
    }

    /** 生成波形峰值 JSON（0~1 归一化），供前端 wavesurfer 预渲染 */
    public String generatePeaksJson(Path file) {
        double[] peaks = null;
        if (ffmpegAvailable()) {
            peaks = peaksViaFfmpeg(file);
        }
        if (peaks == null && file.toString().toLowerCase().endsWith(".wav")) {
            peaks = peaksViaWavParse(file);
        }
        if (peaks == null) {
            // 降级：返回空数组，前端用 wavesurfer 解码生成
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < peaks.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Math.round(peaks[i] * 1000.0) / 1000.0);
        }
        return sb.append(']').toString();
    }

    private double[] peaksViaFfmpeg(Path file) {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-v", "quiet", "-i", file.toString(),
                    "-ac", "1", "-filter:a", "aresample=8000", "-f", "s16le", "-acodec", "pcm_s16le", "-")
                    .redirectErrorStream(true).start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(buffer);
            }
            p.waitFor(120, TimeUnit.SECONDS);
            return computePeaks(buffer.toByteArray());
        } catch (Exception e) {
            log.warn("ffmpeg 波形生成失败: {}", e.getMessage());
            return null;
        }
    }

    private double[] peaksViaWavParse(Path file) {
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            // 跳过 44 字节 WAV 头；最多读取 100MB PCM 防止大文件 OOM
            long skipped = in.skip(44);
            if (skipped < 44) {
                return null;
            }
            byte[] pcm = in.readNBytes(100 * 1024 * 1024);
            return computePeaks(pcm);
        } catch (Exception e) {
            return null;
        }
    }

    private double[] computePeaks(byte[] pcm16le) {
        int samples = pcm16le.length / 2;
        if (samples == 0) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(pcm16le).order(ByteOrder.LITTLE_ENDIAN);
        double[] peaks = new double[PEAKS_COUNT];
        int perBucket = Math.max(1, samples / PEAKS_COUNT);
        for (int bucket = 0; bucket < PEAKS_COUNT; bucket++) {
            int start = bucket * perBucket;
            int end = Math.min(samples, start + perBucket);
            int max = 0;
            for (int i = start; i < end; i++) {
                max = Math.max(max, Math.abs(bb.getShort(i * 2)));
            }
            peaks[bucket] = max / 32768.0;
        }
        return peaks;
    }
}
