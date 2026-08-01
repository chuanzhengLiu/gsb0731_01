package com.podcast.collab.controller;

import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.security.JwtService;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.security.UnauthorizedException;
import com.podcast.collab.service.AudioService;
import com.podcast.collab.service.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AudioController {
    private final AudioService audioService;
    private final AudioVersionRepository audioVersionRepository;
    private final TeamGuard teamGuard;
    private final JwtService jwtService;

    @Value("${app.audio-signed-url-ttl-minutes}")
    private long signedUrlTtlMinutes;

    /** 上传音频新版本（制作人/剪辑师） */
    @PostMapping("/episodes/{episodeId}/audio")
    public AudioVersion upload(@PathVariable Long episodeId, @RequestParam("file") MultipartFile file)
            throws IOException {
        SecurityUtils.requireRole("ADMIN", "PRODUCER", "EDITOR");
        return audioService.upload(episodeId, file);
    }

    @GetMapping("/episodes/{episodeId}/versions")
    public List<Map<String, Object>> versions(@PathVariable Long episodeId) {
        teamGuard.requireEpisode(episodeId);
        return audioVersionRepository.findByEpisodeIdOrderByVersionNumberDesc(episodeId).stream()
                .map(this::toVersionInfo).toList();
    }

    /** 版本对比：相邻版本时长差异 */
    @GetMapping("/episodes/{episodeId}/versions/compare")
    public Map<String, Object> compare(@PathVariable Long episodeId) {
        teamGuard.requireEpisode(episodeId);
        List<AudioVersion> versions = audioVersionRepository
                .findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("versions", versions.stream().map(this::toVersionInfo).toList());
        if (versions.size() >= 2) {
            AudioVersion latest = versions.get(0);
            AudioVersion prev = versions.get(1);
            Long diff = null;
            if (latest.getDurationMs() != null && prev.getDurationMs() != null) {
                diff = latest.getDurationMs() - prev.getDurationMs();
            }
            result.put("latestVersion", latest.getVersionNumber());
            result.put("previousVersion", prev.getVersionNumber());
            result.put("durationDiffMs", diff);
        }
        return result;
    }

    /** 波形数据（预生成 JSON，秒级加载） */
    @GetMapping("/audio/{versionId}/waveform")
    public Map<String, Object> waveform(@PathVariable Long versionId) {
        AudioVersion version = requireVersion(versionId);
        return Map.of(
                "durationMs", version.getDurationMs() == null ? 0 : version.getDurationMs(),
                "peaks", version.getWaveformJson() == null ? "[]" : version.getWaveformJson());
    }

    /** 生成带签名的音频播放 URL（过期失效） */
    @GetMapping("/audio/{versionId}/play-url")
    public Map<String, String> playUrl(@PathVariable Long versionId) {
        AudioVersion version = requireVersion(versionId);
        if ("ARCHIVED".equals(version.getStatus())) {
            throw new IllegalArgumentException("该版本已归档，仅支持下载");
        }
        long expiry = System.currentTimeMillis() / 1000 + signedUrlTtlMinutes * 60;
        String sig = jwtService.signAudioUrl(versionId, expiry);
        return Map.of("url", "/api/audio/stream/" + versionId + "?exp=" + expiry + "&sig=" + sig);
    }

    /** 已归档版本下载 */
    @GetMapping("/audio/{versionId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long versionId) {
        AudioVersion version = requireVersion(versionId);
        return serveFile(version, null, true);
    }

    /** 流式播放（签名 URL 鉴权，支持 Range 拖拽） */
    @GetMapping("/audio/stream/{versionId}")
    public ResponseEntity<Resource> stream(@PathVariable Long versionId,
                                           @RequestParam long exp,
                                           @RequestParam String sig,
                                           @RequestHeader(value = "Range", required = false) String range)
            throws IOException {
        if (!jwtService.verifyAudioSignature(versionId, exp, sig)) {
            throw new UnauthorizedException("音频链接已过期或签名无效");
        }
        AudioVersion version = audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        return serveFile(version, range, false);
    }

    private ResponseEntity<Resource> serveFile(AudioVersion version, String range, boolean attachment) {
        Path path = Paths.get(version.getFileUrl());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("音频文件不存在");
        }
        FileSystemResource resource = new FileSystemResource(path);
        String filename = path.getFileName().toString();
        MediaType mediaType = mediaTypeOf(filename);
        try {
            long length = Files.size(path);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            if (attachment) {
                headers.setContentDispositionFormData("attachment", filename);
            }
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty()
                        ? Long.parseLong(parts[1]) : length - 1;
                end = Math.min(end, length - 1);
                long rangeLength = end - start + 1;
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
                headers.setContentLength(rangeLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .contentType(mediaType)
                        .body(new RangeResource(resource, start, rangeLength));
            }
            headers.setContentLength(length);
            return ResponseEntity.ok().headers(headers).contentType(mediaType).body(resource);
        } catch (IOException e) {
            throw new IllegalStateException("读取音频文件失败");
        }
    }

    private MediaType mediaTypeOf(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (lower.endsWith(".m4a")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (lower.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private AudioVersion requireVersion(Long versionId) {
        AudioVersion version = audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("音频版本不存在"));
        teamGuard.requireEpisode(version.getEpisodeId()); // 团队隔离
        return version;
    }

    private Map<String, Object> toVersionInfo(AudioVersion v) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", v.getId());
        map.put("versionNumber", v.getVersionNumber());
        map.put("durationMs", v.getDurationMs());
        map.put("sampleRate", v.getSampleRate());
        map.put("status", v.getStatus());
        map.put("uploadedBy", v.getUploadedBy());
        map.put("createdAt", v.getCreatedAt());
        return map;
    }
}
