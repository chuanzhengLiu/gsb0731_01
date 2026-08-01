package com.podcast.service.transcription;

import java.nio.file.Path;
import java.util.List;

/**
 * Pluggable transcription backend (README §4.3). Implementations either call a
 * real Whisper backend (OpenAI API / local model) or provide an offline stub,
 * so the feature runs without any API key configured. The provider only turns
 * an audio file into raw, time-aligned segments; persistence and marker/edit
 * logic live in {@code TranscriptionService}.
 */
public interface TranscriptionProvider {

    /** A single time-aligned utterance produced by the backend. */
    record RawSegment(long startTimeMs, long endTimeMs, String text, String speaker) {}

    /** Human-readable provider name, surfaced in audit/logs. */
    String name();

    /** Whether this provider is usable in the current environment. */
    boolean isAvailable();

    /**
     * Transcribes the given audio file. {@code durationMs} may be null when the
     * duration is unknown (e.g. ffprobe unavailable); implementations should
     * still return a sensible result.
     */
    List<RawSegment> transcribe(Path audioFile, Long durationMs);
}
