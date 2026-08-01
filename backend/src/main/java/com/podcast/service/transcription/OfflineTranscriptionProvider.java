package com.podcast.service.transcription;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline degraded transcription (README §4.3 "或本地部署 Whisper"): used when
 * no API key is configured and no local model is wired up, so the transcription
 * feature stays runnable end-to-end without external dependencies. It produces
 * deterministic placeholder segments spanning the audio duration, alternating
 * between two speakers so the alignment / speaker-colour UI can be exercised.
 *
 * This is intentionally a stub — swapping in a real local Whisper binding only
 * means adding another {@link TranscriptionProvider} implementation.
 */
@Component
public class OfflineTranscriptionProvider implements TranscriptionProvider {

    /** Target length of each synthetic segment. */
    private static final long SEGMENT_MS = 8000;
    private static final long DEFAULT_TOTAL_MS = 32000;

    @Override
    public String name() {
        return "offline-stub";
    }

    @Override
    public boolean isAvailable() {
        return true; // always the last-resort fallback
    }

    @Override
    public List<RawSegment> transcribe(Path audioFile, Long durationMs) {
        long total = (durationMs != null && durationMs > 0) ? durationMs : DEFAULT_TOTAL_MS;
        List<RawSegment> out = new ArrayList<>();
        int index = 0;
        for (long start = 0; start < total; start += SEGMENT_MS) {
            long end = Math.min(start + SEGMENT_MS, total);
            String speaker = (index % 2 == 0) ? "主播A" : "嘉宾B";
            String text = "[自动转写占位] 第 " + (index + 1) + " 段（"
                    + fmt(start) + "–" + fmt(end) + "），未配置 Whisper，人工可在此修正文本。";
            out.add(new RawSegment(start, end, text, speaker));
            index++;
        }
        if (out.isEmpty()) {
            out.add(new RawSegment(0, total,
                    "[自动转写占位] 未配置 Whisper，人工可在此修正文本。", "主播A"));
        }
        return out;
    }

    private String fmt(long ms) {
        long total = ms / 1000;
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }
}
