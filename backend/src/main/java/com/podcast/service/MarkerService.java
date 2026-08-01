package com.podcast.service;

import com.podcast.domain.AudioVersion;
import com.podcast.domain.MarkerStatus;
import com.podcast.domain.MarkerType;
import com.podcast.domain.TimelineMarker;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.repository.TimelineMarkerRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import com.podcast.web.dto.MarkerDtos.CreateMarkerRequest;
import com.podcast.web.dto.MarkerDtos.UpdateMarkerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MarkerService {

    private final TimelineMarkerRepository repo;
    private final AudioVersionRepository audioRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final AuditService audit;

    public MarkerService(TimelineMarkerRepository repo, AudioVersionRepository audioRepo,
                         AccessGuard accessGuard, AuthorizationService authz, AuditService audit) {
        this.repo = repo;
        this.audioRepo = audioRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.audit = audit;
    }

    /**
     * Whether the current user may annotate the given episode's timeline
     * (create markers / edit transcript text). Exposed for TranscriptionService
     * so text-anchored annotations follow the same §9 role rules.
     */
    public void checkCanAnnotate(Long episodeId) {
        authz.checkCanCreateMarker(episodeId);
    }

    private AudioVersion requireAudioVersion(Long audioVersionId) {
        AudioVersion v = audioRepo.findById(audioVersionId)
                .orElseThrow(() -> ApiException.notFound("音频版本不存在"));
        accessGuard.requireEpisode(v.getEpisodeId()); // team isolation
        return v;
    }

    private TimelineMarker requireMarker(Long markerId) {
        TimelineMarker m = repo.findById(markerId)
                .orElseThrow(() -> ApiException.notFound("标记不存在"));
        requireAudioVersion(m.getAudioVersionId());
        return m;
    }

    @Transactional(readOnly = true)
    public List<TimelineMarker> list(Long audioVersionId) {
        requireAudioVersion(audioVersionId);
        return repo.findByAudioVersionIdOrderByStartTimeMsAsc(audioVersionId);
    }

    /** Filter + keyword search (README §4.2). */
    @Transactional(readOnly = true)
    public List<TimelineMarker> search(Long audioVersionId, MarkerType type,
                                       MarkerStatus status, Long createdBy, String keyword) {
        requireAudioVersion(audioVersionId);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return repo.search(audioVersionId, type, status, createdBy, kw);
    }

    @Transactional
    public TimelineMarker create(Long audioVersionId, CreateMarkerRequest req) {
        AudioVersion v = requireAudioVersion(audioVersionId);
        authz.checkCanCreateMarker(v.getEpisodeId()); // README §9 role rules
        if (req.startTimeMs() < 0) {
            throw ApiException.badRequest("时间点不能为负");
        }
        if (req.endTimeMs() != null && req.endTimeMs() < req.startTimeMs()) {
            throw ApiException.badRequest("结束时间不能早于开始时间");
        }
        TimelineMarker m = new TimelineMarker();
        m.setAudioVersionId(audioVersionId);
        m.setStartTimeMs(req.startTimeMs());
        // endTimeMs != null => a time-range marker (README §4.2 拖拽选段);
        // endTimeMs == null => a point marker (P0).
        m.setEndTimeMs(req.endTimeMs());
        m.setType(req.type());
        m.setDescription(req.description());
        m.setScreenshotUrl(req.screenshotUrl());
        m.setStatus(MarkerStatus.PENDING);
        m.setCreatedBy(SecurityUtils.currentUserId());
        m = repo.save(m);
        audit.log("MARKER_CREATE", "TimelineMarker", m.getId(),
                "audioVersion=" + audioVersionId
                        + " type=" + req.type()
                        + (req.endTimeMs() != null ? " range" : " point"));
        return m;
    }

    @Transactional
    public TimelineMarker update(Long markerId, UpdateMarkerRequest req) {
        TimelineMarker m = requireMarker(markerId);
        AudioVersion v = requireAudioVersion(m.getAudioVersionId());

        // A status change follows the stricter status-flow authorization
        // (README §4.2/§9); other field edits use the general modify rule.
        boolean statusChanging = req.status() != null && req.status() != m.getStatus();
        boolean fieldsChanging = req.startTimeMs() != null || req.endTimeMs() != null
                || req.type() != null || req.description() != null || req.screenshotUrl() != null;

        if (fieldsChanging) {
            authz.checkCanModifyMarker(v.getEpisodeId(), m.getCreatedBy());
        }
        if (statusChanging) {
            authz.checkCanChangeMarkerStatus(v.getEpisodeId(), m.getCreatedBy());
            if (!m.getStatus().canTransitionTo(req.status())) {
                throw ApiException.badRequest(
                        "非法的状态流转：" + m.getStatus() + " → " + req.status());
            }
        }

        if (req.startTimeMs() != null) m.setStartTimeMs(req.startTimeMs());
        if (req.endTimeMs() != null) m.setEndTimeMs(req.endTimeMs());
        if (req.type() != null) m.setType(req.type());
        if (req.description() != null) m.setDescription(req.description());
        if (req.screenshotUrl() != null) m.setScreenshotUrl(req.screenshotUrl());
        if (req.status() != null) m.setStatus(req.status());
        if (m.getEndTimeMs() != null && m.getEndTimeMs() < m.getStartTimeMs()) {
            throw ApiException.badRequest("结束时间不能早于开始时间");
        }
        m = repo.save(m);
        audit.log("MARKER_UPDATE", "TimelineMarker", m.getId(), "status=" + m.getStatus());
        return m;
    }

    /** Dedicated status-flow endpoint (README §4.2 标记状态流转). */
    @Transactional
    public TimelineMarker changeStatus(Long markerId, MarkerStatus target) {
        TimelineMarker m = requireMarker(markerId);
        AudioVersion v = requireAudioVersion(m.getAudioVersionId());
        authz.checkCanChangeMarkerStatus(v.getEpisodeId(), m.getCreatedBy());
        if (!m.getStatus().canTransitionTo(target)) {
            throw ApiException.badRequest(
                    "非法的状态流转：" + m.getStatus() + " → " + target);
        }
        MarkerStatus from = m.getStatus();
        m.setStatus(target);
        m = repo.save(m);
        audit.log("MARKER_STATUS_CHANGE", "TimelineMarker", m.getId(), from + " -> " + target);
        return m;
    }

    @Transactional
    public void delete(Long markerId) {
        TimelineMarker m = requireMarker(markerId);
        AudioVersion v = requireAudioVersion(m.getAudioVersionId());
        authz.checkCanModifyMarker(v.getEpisodeId(), m.getCreatedBy()); // README §9
        repo.delete(m);
        audit.log("MARKER_DELETE", "TimelineMarker", markerId, null);
    }
}
