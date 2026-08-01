package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.AudioVersion;
import com.podcast.collab.domain.TranscriptSegment;
import com.podcast.collab.dto.TranscriptDtos.*;
import com.podcast.collab.repo.AudioVersionRepository;
import com.podcast.collab.repo.TranscriptSegmentRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TranscriptService {

    private final TranscriptSegmentRepository segmentRepo;
    private final AudioVersionRepository versionRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    public TranscriptService(TranscriptSegmentRepository segmentRepo, AudioVersionRepository versionRepo,
                             TeamGuard teamGuard, AuditService auditService) {
        this.segmentRepo = segmentRepo;
        this.versionRepo = versionRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SegmentResponse> list(Long versionId, CurrentUser user) {
        AudioVersion v = requireVersion(versionId, user);
        return segmentRepo.findByAudioVersionIdOrderBySeqAsc(v.getId()).stream()
                .map(s -> new SegmentResponse(s.getId(), s.getAudioVersionId(), s.getStartTimeMs(),
                        s.getEndTimeMs(), s.getText(), s.getSpeaker(), s.getSeq()))
                .toList();
    }

    @Transactional
    public List<SegmentResponse> save(Long versionId, SaveTranscriptRequest req, CurrentUser user) {
        AudioVersion v = requireVersion(versionId, user);
        segmentRepo.deleteByAudioVersionId(v.getId());
        List<TranscriptSegment> saved = new ArrayList<>();
        int seq = 0;
        for (SegmentInput input : req.segments()) {
            if (input.endTimeMs() < input.startTimeMs()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Segment endTime must be >= startTime");
            }
            TranscriptSegment s = new TranscriptSegment();
            s.setAudioVersionId(v.getId());
            s.setStartTimeMs(input.startTimeMs());
            s.setEndTimeMs(input.endTimeMs());
            s.setText(input.text());
            s.setSpeaker(input.speaker());
            s.setSeq(seq++);
            saved.add(segmentRepo.save(s));
        }
        auditService.log(user, "TRANSCRIPT_SAVE", "AudioVersion", versionId,
                Map.of("segments", saved.size()));
        return saved.stream()
                .map(s -> new SegmentResponse(s.getId(), s.getAudioVersionId(), s.getStartTimeMs(),
                        s.getEndTimeMs(), s.getText(), s.getSpeaker(), s.getSeq()))
                .toList();
    }

    private AudioVersion requireVersion(Long versionId, CurrentUser user) {
        AudioVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Audio version not found"));
        teamGuard.requireEpisode(v.getEpisodeId(), user);
        return v;
    }
}
