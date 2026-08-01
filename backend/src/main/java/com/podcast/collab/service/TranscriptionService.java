package com.podcast.collab.service;

import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.TranscriptSegment;
import com.podcast.collab.repository.TranscriptSegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 转写服务：可选接入 OpenAI Whisper API（配置 WHISPER_ENABLED=true），
 * 也支持前端手工导入转写片段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {
    private final TranscriptSegmentRepository segmentRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.whisper.enabled}")
    private boolean whisperEnabled;
    @Value("${app.whisper.api-url}")
    private String whisperApiUrl;
    @Value("${app.whisper.api-key}")
    private String whisperApiKey;

    public boolean isEnabled() {
        return whisperEnabled && whisperApiKey != null && !whisperApiKey.isBlank();
    }

    public List<TranscriptSegment> list(Long audioVersionId) {
        return segmentRepository.findByAudioVersionIdOrderByStartTimeMs(audioVersionId);
    }

    /** 调用 Whisper API 生成转写（verbose_json 带词级时间戳） */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<TranscriptSegment> transcribe(AudioVersion version) {
        if (!isEnabled()) {
            throw new IllegalStateException("未配置 Whisper（WHISPER_ENABLED/WHISPER_API_KEY），请使用手工导入");
        }
        Path path = Paths.get(version.getFileUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(whisperApiKey);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(path));
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(whisperApiUrl, entity, Map.class);
        Map<String, Object> result = response.getBody();
        if (result == null || !result.containsKey("segments")) {
            throw new IllegalStateException("Whisper 返回结果为空");
        }
        segmentRepository.deleteByAudioVersionId(version.getId());
        List<Map<String, Object>> segments = (List<Map<String, Object>>) result.get("segments");
        for (Map<String, Object> seg : segments) {
            TranscriptSegment ts = new TranscriptSegment();
            ts.setAudioVersionId(version.getId());
            ts.setStartTimeMs(Math.round(((Number) seg.get("start")).doubleValue() * 1000));
            ts.setEndTimeMs(Math.round(((Number) seg.get("end")).doubleValue() * 1000));
            ts.setText(((String) seg.get("text")).trim());
            ts.setSpeaker(seg.get("speaker") == null ? null : seg.get("speaker").toString());
            segmentRepository.save(ts);
        }
        return list(version.getId());
    }

    /** 手工导入转写片段（替换旧数据） */
    @Transactional
    public List<TranscriptSegment> importSegments(Long audioVersionId,
                                                  List<com.podcast.collab.dto.Dtos.TranscriptSegmentRequest> segments) {
        segmentRepository.deleteByAudioVersionId(audioVersionId);
        for (var req : segments) {
            TranscriptSegment ts = new TranscriptSegment();
            ts.setAudioVersionId(audioVersionId);
            ts.setStartTimeMs(req.startTimeMs());
            ts.setEndTimeMs(req.endTimeMs());
            ts.setText(req.text());
            ts.setSpeaker(req.speaker());
            segmentRepository.save(ts);
        }
        return list(audioVersionId);
    }

    /** 人工修正转写文本，修正后时间戳按比例自动调整 */
    public TranscriptSegment editSegment(Long segmentId, String newText, Long newStartMs, Long newEndMs) {
        TranscriptSegment ts = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new IllegalArgumentException("转写片段不存在"));
        ts.setText(newText);
        if (newStartMs != null) {
            ts.setStartTimeMs(newStartMs);
        }
        if (newEndMs != null) {
            ts.setEndTimeMs(newEndMs);
        }
        ts.setEdited(true);
        return segmentRepository.save(ts);
    }
}
