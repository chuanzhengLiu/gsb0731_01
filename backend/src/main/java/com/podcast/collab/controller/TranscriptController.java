package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.TranscriptSegment;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.TranscriptSegmentRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TeamGuard;
import com.podcast.collab.service.TranscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TranscriptController {
    private final TranscriptionService transcriptionService;
    private final AudioVersionRepository audioVersionRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final TeamGuard teamGuard;

    @GetMapping("/audio/{versionId}/transcript")
    public List<TranscriptSegment> list(@PathVariable Long versionId) {
        requireVersion(versionId);
        return transcriptionService.list(versionId);
    }

    /** 调用 Whisper 生成转写 */
    @PostMapping("/audio/{versionId}/transcript/generate")
    public List<TranscriptSegment> generate(@PathVariable Long versionId) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER", "EDITOR");
        AudioVersion version = requireVersion(versionId);
        return transcriptionService.transcribe(version);
    }

    /** 手工导入转写片段 */
    @PostMapping("/audio/{versionId}/transcript/import")
    public List<TranscriptSegment> importSegments(@PathVariable Long versionId,
                                                  @Valid @RequestBody TranscriptImportRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER", "EDITOR");
        requireVersion(versionId);
        return transcriptionService.importSegments(versionId, req.segments());
    }

    /** 修正转写文本 */
    @PutMapping("/transcript/segments/{id}")
    public TranscriptSegment edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        TranscriptSegment ts = segmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("转写片段不存在"));
        requireVersion(ts.getAudioVersionId());
        Long start = body.get("startTimeMs") == null ? null : ((Number) body.get("startTimeMs")).longValue();
        Long end = body.get("endTimeMs") == null ? null : ((Number) body.get("endTimeMs")).longValue();
        return transcriptionService.editSegment(id, (String) body.get("text"), start, end);
    }

    @GetMapping("/transcript/enabled")
    public Map<String, Boolean> enabled() {
        return Map.of("whisperEnabled", transcriptionService.isEnabled());
    }

    private AudioVersion requireVersion(Long versionId) {
        AudioVersion version = audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("音频版本不存在"));
        teamGuard.requireEpisode(version.getEpisodeId());
        return version;
    }
}
