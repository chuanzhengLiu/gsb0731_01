package com.podcast.collab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.dto.transcript.BulkTranscriptRequest;
import com.podcast.collab.dto.transcript.TranscriptSegmentResponse;
import com.podcast.collab.dto.transcript.UpdateTranscriptSegmentRequest;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.TranscriptSegment;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TranscriptSegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TranscriptService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptService.class);

    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final String whisperApiKey;
    private final String whisperApiUrl;
    private final String whisperModel;

    private final HttpClient httpClient;

    public TranscriptService(TranscriptSegmentRepository transcriptSegmentRepository,
                             AudioVersionRepository audioVersionRepository,
                             EpisodeRepository episodeRepository,
                             PodcastRepository podcastRepository,
                             TeamMemberRepository teamMemberRepository,
                             AuditService auditService,
                             FileStorageService fileStorageService,
                             ObjectMapper objectMapper,
                             @Value("${app.whisper.api-key:}") String whisperApiKey,
                             @Value("${app.whisper.api-url:https://api.openai.com/v1/audio/transcriptions}") String whisperApiUrl,
                             @Value("${app.whisper.model:whisper-1}") String whisperModel) {
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.audioVersionRepository = audioVersionRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.whisperApiKey = whisperApiKey;
        this.whisperApiUrl = whisperApiUrl;
        this.whisperModel = whisperModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Transactional(readOnly = true)
    public List<TranscriptSegmentResponse> getSegments(Long versionId, Long userId) {
        verifyVersionTeamAccess(versionId, userId);
        return transcriptSegmentRepository.findByAudioVersionIdOrderBySegmentOrderAsc(versionId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public List<TranscriptSegmentResponse> bulkUpload(Long versionId, BulkTranscriptRequest request, Long userId) {
        verifyVersionTeamAccess(versionId, userId);

        transcriptSegmentRepository.deleteByAudioVersionId(versionId);

        List<TranscriptSegment> segments = new ArrayList<>();
        List<BulkTranscriptRequest.Segment> incoming = request.getSegments();
        for (int i = 0; i < incoming.size(); i++) {
            BulkTranscriptRequest.Segment dto = incoming.get(i);
            TranscriptSegment segment = TranscriptSegment.builder()
                    .audioVersionId(versionId)
                    .startTimeMs(dto.getStartTimeMs())
                    .endTimeMs(dto.getEndTimeMs())
                    .text(dto.getText())
                    .speaker(dto.getSpeaker())
                    .segmentOrder(i)
                    .build();
            segments.add(segment);
        }
        segments = transcriptSegmentRepository.saveAll(segments);

        auditService.log(userId, "BULK_UPLOAD_TRANSCRIPT", "AudioVersion", versionId,
                "Bulk uploaded " + segments.size() + " transcript segments");

        return segments.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public TranscriptSegmentResponse updateSegment(Long segmentId, UpdateTranscriptSegmentRequest request, Long userId) {
        TranscriptSegment segment = findSegmentOrThrow(segmentId);
        verifyVersionTeamAccess(segment.getAudioVersionId(), userId);

        if (request.getText() != null) {
            segment.setText(request.getText());
        }
        if (request.getSpeaker() != null) {
            segment.setSpeaker(request.getSpeaker());
        }
        if (request.getStartTimeMs() != null) {
            segment.setStartTimeMs(request.getStartTimeMs());
        }
        if (request.getEndTimeMs() != null) {
            segment.setEndTimeMs(request.getEndTimeMs());
        }
        segment = transcriptSegmentRepository.save(segment);

        auditService.log(userId, "UPDATE_TRANSCRIPT_SEGMENT", "TranscriptSegment", segmentId,
                "Transcript segment updated");

        return mapToResponse(segment);
    }

    @Transactional
    public void deleteSegment(Long segmentId, Long userId) {
        TranscriptSegment segment = findSegmentOrThrow(segmentId);
        verifyVersionTeamAccess(segment.getAudioVersionId(), userId);
        transcriptSegmentRepository.delete(segment);

        auditService.log(userId, "DELETE_TRANSCRIPT_SEGMENT", "TranscriptSegment", segmentId,
                "Transcript segment deleted");
    }

    @Transactional
    public List<TranscriptSegmentResponse> generateTranscript(Long versionId, Long userId) {
        verifyVersionTeamAccess(versionId, userId);

        if (whisperApiKey == null || whisperApiKey.isBlank()) {
            throw new BadRequestException(
                "Whisper API 未配置。请在 application.yml 中设置 app.whisper.api-key，或通过环境变量 WHISPER_API_KEY 配置 OpenAI API Key 后再使用自动生成功能。");
        }

        AudioVersion version = findVersionOrThrow(versionId);
        Path audioPath = fileStorageService.getAudioPath(version.getFileUrl());
        if (!Files.exists(audioPath)) {
            throw new ResourceNotFoundException("Audio file not found on server: " + version.getFileUrl());
        }

        long fileSizeBytes;
        try {
            fileSizeBytes = Files.size(audioPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file size", e);
        }
        if (fileSizeBytes > 25L * 1024 * 1024) {
            throw new BadRequestException(
                "音频文件大小 (" + (fileSizeBytes / 1024 / 1024) + "MB) 超过 Whisper API 的 25MB 限制，请压缩或截取后重试。");
        }

        log.info("Calling Whisper API for audio version {} ({} bytes)", versionId, fileSizeBytes);

        List<WhisperSegment> whisperSegments;
        try {
            whisperSegments = callWhisperApi(audioPath, version.getFileName());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Whisper API call failed for version {}", versionId, e);
            throw new BadRequestException("Whisper API 调用失败: " + e.getMessage());
        }

        if (whisperSegments.isEmpty()) {
            throw new BadRequestException("Whisper API 未返回任何转写片段，请检查音频文件是否包含可识别的语音。");
        }

        // API 调用成功后，才清除旧数据并写入新结果
        transcriptSegmentRepository.deleteByAudioVersionId(versionId);

        List<TranscriptSegment> segments = new ArrayList<>();
        for (int i = 0; i < whisperSegments.size(); i++) {
            WhisperSegment ws = whisperSegments.get(i);
            TranscriptSegment segment = TranscriptSegment.builder()
                    .audioVersionId(versionId)
                    .startTimeMs(ws.startMs())
                    .endTimeMs(ws.endMs())
                    .text(ws.text() != null ? ws.text().trim() : "")
                    .speaker(null)
                    .segmentOrder(i)
                    .build();
            segments.add(segment);
        }
        segments = transcriptSegmentRepository.saveAll(segments);

        auditService.log(userId, "GENERATE_TRANSCRIPT", "AudioVersion", versionId,
                "Whisper generated " + segments.size() + " transcript segments");

        log.info("Whisper generated {} transcript segments for version {}", segments.size(), versionId);

        return segments.stream().map(this::mapToResponse).toList();
    }

    private List<WhisperSegment> callWhisperApi(Path audioPath, String fileName) throws IOException, InterruptedException {
        String boundary = "----PodcastCollab" + UUID.randomUUID().toString().replace("-", "");

        String mimeType = resolveMimeType(fileName);
        String actualFileName = fileName != null ? fileName : audioPath.getFileName().toString();

        byte[] fileBytes = Files.readAllBytes(audioPath);

        HttpRequest.BodyPublisher bodyPublisher = buildMultipartBody(
                boundary,
                fileBytes,
                actualFileName,
                mimeType
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(whisperApiUrl))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + whisperApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(bodyPublisher)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode < 200 || statusCode >= 300) {
            String errorMsg = extractApiErrorMessage(responseBody);
            log.error("Whisper API returned status {}: {}", statusCode, errorMsg);
            throw new BadRequestException("Whisper API 返回错误 (" + statusCode + "): " + errorMsg);
        }

        return parseWhisperResponse(responseBody);
    }

    private HttpRequest.BodyPublisher buildMultipartBody(String boundary,
                                                         byte[] fileBytes,
                                                         String fileName,
                                                         String mimeType) {
        String charset = StandardCharsets.UTF_8.name();
        String lineEnd = "\r\n";

        java.util.List<byte[]> parts = new java.util.ArrayList<>();

        // model field
        parts.add(("--" + boundary + lineEnd +
                "Content-Disposition: form-data; name=\"model\"" + lineEnd + lineEnd +
                whisperModel + lineEnd).getBytes(StandardCharsets.UTF_8));

        // response_format field
        parts.add(("--" + boundary + lineEnd +
                "Content-Disposition: form-data; name=\"response_format\"" + lineEnd + lineEnd +
                "verbose_json" + lineEnd).getBytes(StandardCharsets.UTF_8));

        // file field header
        String fileHeader = "--" + boundary + lineEnd +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + lineEnd +
                "Content-Type: " + mimeType + lineEnd + lineEnd;
        parts.add(fileHeader.getBytes(StandardCharsets.UTF_8));
        parts.add(fileBytes);
        parts.add(lineEnd.getBytes(StandardCharsets.UTF_8));

        // closing boundary
        parts.add(("--" + boundary + "--" + lineEnd).getBytes(StandardCharsets.UTF_8));

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private List<WhisperSegment> parseWhisperResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        List<WhisperSegment> result = new ArrayList<>();

        JsonNode segmentsNode = root.path("segments");
        if (segmentsNode.isArray() && segmentsNode.size() > 0) {
            for (JsonNode seg : segmentsNode) {
                double startSec = seg.path("start").asDouble(0);
                double endSec = seg.path("end").asDouble(startSec);
                String text = seg.path("text").asText("");
                result.add(new WhisperSegment(
                        (long) (startSec * 1000),
                        (long) (endSec * 1000),
                        text
                ));
            }
        } else {
            String fullText = root.path("text").asText("");
            if (!fullText.isBlank()) {
                result.add(new WhisperSegment(0L, 0L, fullText));
            }
        }

        return result;
    }

    private String extractApiErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                return error.path("message").asText(responseBody);
            }
        } catch (Exception ignored) {
        }
        if (responseBody.length() > 300) {
            return responseBody.substring(0, 300) + "...";
        }
        return responseBody;
    }

    private String resolveMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "application/octet-stream";
    }

    private record WhisperSegment(long startMs, long endMs, String text) {}

    private TranscriptSegment findSegmentOrThrow(Long segmentId) {
        return transcriptSegmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Transcript segment not found"));
    }

    private AudioVersion findVersionOrThrow(Long versionId) {
        return audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Audio version not found"));
    }

    private Episode findEpisodeOrThrow(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyVersionTeamAccess(Long versionId, Long userId) {
        AudioVersion version = findVersionOrThrow(versionId);
        Episode episode = findEpisodeOrThrow(version.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        if (!teamMemberRepository.existsByTeamIdAndUserId(podcast.getTeamId(), userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private TranscriptSegmentResponse mapToResponse(TranscriptSegment segment) {
        return TranscriptSegmentResponse.builder()
                .id(segment.getId())
                .audioVersionId(segment.getAudioVersionId())
                .startTimeMs(segment.getStartTimeMs())
                .endTimeMs(segment.getEndTimeMs())
                .text(segment.getText())
                .speaker(segment.getSpeaker())
                .segmentOrder(segment.getSegmentOrder())
                .createdAt(segment.getCreatedAt())
                .updatedAt(segment.getUpdatedAt())
                .build();
    }
}
