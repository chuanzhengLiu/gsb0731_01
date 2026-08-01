package com.podcast.service;

import com.podcast.domain.AudioVersion;
import com.podcast.domain.TimelineMarker;
import com.podcast.domain.TranscriptSegment;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.repository.TranscriptSegmentRepository;
import com.podcast.service.transcription.TranscriptionProvider;
import com.podcast.web.ApiException;
import com.podcast.web.dto.MarkerDtos.CreateMarkerRequest;
import com.podcast.web.dto.TranscriptDtos.SegmentMarkerRequest;
import com.podcast.web.dto.TranscriptDtos.UpdateSegmentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transcript alignment (README §4.3): generates a time-aligned transcript on
 * upload via a pluggable {@link TranscriptionProvider}, supports jumping to a
 * sentence, adding markers straight from the text, speaker separation (colour),
 * and human correction with automatic timestamp re-adjustment.
 */
@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    /** Stable palette so each speaker keeps the same colour across a transcript. */
    private static final String[] SPEAKER_PALETTE = {
            "#2563eb", "#db2777", "#059669", "#d97706", "#7c3aed", "#0891b2"
    };

    private final TranscriptSegmentRepository segmentRepo;
    private final AudioVersionRepository audioRepo;
    private final AccessGuard accessGuard;
    private final StorageService storage;
    private final MarkerService markerService;
    private final AuditService audit;
    private final List<TranscriptionProvider> providers;

    public TranscriptionService(TranscriptSegmentRepository segmentRepo,
                                AudioVersionRepository audioRepo, AccessGuard accessGuard,
                                StorageService storage, MarkerService markerService,
                                AuditService audit, List<TranscriptionProvider> providers) {
        this.segmentRepo = segmentRepo;
        this.audioRepo = audioRepo;
        this.accessGuard = accessGuard;
        this.storage = storage;
        this.markerService = markerService;
        this.audit = audit;
        this.providers = providers;
    }

    private AudioVersion requireAudioVersion(Long audioVersionId) {
        AudioVersion v = audioRepo.findById(audioVersionId)
                .orElseThrow(() -> ApiException.notFound("音频版本不存在"));
        accessGuard.requireEpisode(v.getEpisodeId()); // team isolation
        return v;
    }

    /** Picks the best available provider: a real backend if usable, else offline. */
    private TranscriptionProvider selectProvider() {
        TranscriptionProvider fallback = null;
        for (TranscriptionProvider p : providers) {
            if ("offline-stub".equals(p.name())) {
                fallback = p;
            } else if (p.isAvailable()) {
                return p;
            }
        }
        return fallback != null ? fallback : providers.get(0);
    }

    /**
     * Generates (or regenerates) the transcript for an audio version. Called
     * automatically after upload; also exposed for manual re-run. Best-effort:
     * a provider failure logs and leaves any existing transcript untouched.
     */
    @Transactional
    public List<TranscriptSegment> generate(Long audioVersionId) {
        AudioVersion v = requireAudioVersion(audioVersionId);
        return generateFor(v, true);
    }

    /**
     * Internal generation used by the upload pipeline (no separate access check;
     * the caller already validated the episode). Returns the persisted segments.
     */
    @Transactional
    public List<TranscriptSegment> generateFor(AudioVersion v, boolean replaceExisting) {
        TranscriptionProvider provider = selectProvider();
        Path path = storage.resolve(v.getFileKey());
        List<TranscriptionProvider.RawSegment> raw;
        try {
            raw = provider.transcribe(path, v.getDurationMs());
        } catch (Exception e) {
            log.warn("transcription provider {} failed for av={}: {}",
                    provider.name(), v.getId(), e.getMessage());
            raw = List.of();
        }
        if (raw.isEmpty()) {
            log.info("no transcript produced for av={} via {}", v.getId(), provider.name());
            return replaceExisting
                    ? segmentRepo.findByAudioVersionIdOrderBySegmentIndexAsc(v.getId())
                    : List.of();
        }

        if (replaceExisting) {
            segmentRepo.deleteByAudioVersionId(v.getId());
        }

        Map<String, String> speakerColors = new LinkedHashMap<>();
        List<TranscriptSegment> saved = new ArrayList<>();
        int index = 0;
        for (TranscriptionProvider.RawSegment r : raw) {
            TranscriptSegment s = new TranscriptSegment();
            s.setAudioVersionId(v.getId());
            s.setSegmentIndex(index++);
            s.setStartTimeMs(Math.max(0, r.startTimeMs()));
            s.setEndTimeMs(Math.max(r.startTimeMs(), r.endTimeMs()));
            s.setText(r.text());
            s.setSpeaker(r.speaker());
            if (r.speaker() != null) {
                s.setSpeakerColor(colorFor(r.speaker(), speakerColors));
            }
            saved.add(segmentRepo.save(s));
        }
        audit.log("TRANSCRIPT_GENERATE", "AudioVersion", v.getId(),
                "provider=" + provider.name() + " segments=" + saved.size());
        return saved;
    }

    private String colorFor(String speaker, Map<String, String> assigned) {
        return assigned.computeIfAbsent(speaker,
                k -> SPEAKER_PALETTE[assigned.size() % SPEAKER_PALETTE.length]);
    }

    @Transactional(readOnly = true)
    public List<TranscriptSegment> list(Long audioVersionId) {
        requireAudioVersion(audioVersionId);
        return segmentRepo.findByAudioVersionIdOrderBySegmentIndexAsc(audioVersionId);
    }

    private TranscriptSegment requireSegment(Long segmentId) {
        TranscriptSegment s = segmentRepo.findById(segmentId)
                .orElseThrow(() -> ApiException.notFound("转写片段不存在"));
        requireAudioVersion(s.getAudioVersionId()); // team isolation
        return s;
    }

    /**
     * Human correction of a segment's text (README §4.3 转写编辑). After the edit
     * the timestamps of the whole transcript are re-adjusted so each segment's
     * span is proportional to its (possibly changed) text length — keeping the
     * transcript aligned to the audio without manual re-timing.
     */
    @Transactional
    public TranscriptSegment updateSegment(Long segmentId, UpdateSegmentRequest req) {
        TranscriptSegment target = requireSegment(segmentId);
        markerAuthzForTranscript(target.getAudioVersionId());

        target.setText(req.text());
        if (req.speaker() != null) {
            target.setSpeaker(req.speaker());
        }
        target.setEdited(true);
        segmentRepo.save(target);

        recomputeTimestamps(target.getAudioVersionId());
        audit.log("TRANSCRIPT_EDIT", "TranscriptSegment", target.getId(), null);
        return requireSegment(segmentId);
    }

    /**
     * Redistributes the transcript's timeline proportionally to each segment's
     * text length, preserving the overall start..end span. This is the automatic
     * timestamp adjustment referenced in README §4.3.
     */
    private void recomputeTimestamps(Long audioVersionId) {
        List<TranscriptSegment> segs = segmentRepo.findByAudioVersionIdOrderBySegmentIndexAsc(audioVersionId);
        if (segs.size() < 2) {
            return; // nothing to redistribute
        }
        long spanStart = segs.get(0).getStartTimeMs();
        long spanEnd = segs.get(segs.size() - 1).getEndTimeMs();
        long span = spanEnd - spanStart;
        if (span <= 0) {
            return;
        }
        long totalChars = 0;
        for (TranscriptSegment s : segs) {
            totalChars += Math.max(1, s.getText() == null ? 0 : s.getText().length());
        }
        if (totalChars <= 0) {
            return;
        }
        long cursor = spanStart;
        for (int i = 0; i < segs.size(); i++) {
            TranscriptSegment s = segs.get(i);
            long chars = Math.max(1, s.getText() == null ? 0 : s.getText().length());
            long dur = Math.round((double) span * chars / totalChars);
            long start = cursor;
            long end = (i == segs.size() - 1) ? spanEnd : Math.min(spanEnd, cursor + dur);
            if (end < start) {
                end = start;
            }
            s.setStartTimeMs(start);
            s.setEndTimeMs(end);
            segmentRepo.save(s);
            cursor = end;
        }
    }

    /**
     * Adds a marker anchored to a transcript segment (README §4.3). Delegates to
     * {@link MarkerService} so the §9 role rules apply uniformly.
     */
    @Transactional
    public TimelineMarker addMarkerForSegment(Long segmentId, SegmentMarkerRequest req) {
        TranscriptSegment s = requireSegment(segmentId);
        Long endTime = req.asRange() ? s.getEndTimeMs() : null;
        CreateMarkerRequest markerReq = new CreateMarkerRequest(
                s.getStartTimeMs(), endTime, req.type(), req.description(), null);
        return markerService.create(s.getAudioVersionId(), markerReq);
    }

    /** Editing transcript text follows the same authority as modifying markers. */
    private void markerAuthzForTranscript(Long audioVersionId) {
        AudioVersion v = requireAudioVersion(audioVersionId);
        // Reuse the create-marker permission: producers/editors(assigned)/hosts
        // may collaborate on the timeline; operators/guests may not.
        markerService.checkCanAnnotate(v.getEpisodeId());
    }
}
