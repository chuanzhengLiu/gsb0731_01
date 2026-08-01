package com.podcast.collab.controller;

import com.podcast.collab.dto.audio.AudioVersionResponse;
import com.podcast.collab.dto.audio.UploadAudioResponse;
import com.podcast.collab.dto.audio.WaveformResponse;
import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AudioVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/audio")
public class AudioController {

    private final AudioVersionService audioVersionService;

    public AudioController(AudioVersionService audioVersionService) {
        this.audioVersionService = audioVersionService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadAudioResponse> uploadAudio(@RequestParam Long episodeId,
                                                        @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        UploadAudioResponse response = audioVersionService.uploadAudio(episodeId, file, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/version/{versionId}")
    public ApiResponse<AudioVersionResponse> getVersion(@PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AudioVersionResponse response = audioVersionService.getVersion(versionId, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/episode/{episodeId}/versions")
    public ApiResponse<List<AudioVersionResponse>> listVersions(@PathVariable Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AudioVersionResponse> response = audioVersionService.listVersions(episodeId, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/episode/{episodeId}/latest")
    public ApiResponse<AudioVersionResponse> getLatestVersion(@PathVariable Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AudioVersionResponse response = audioVersionService.getLatestVersion(episodeId, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/version/{versionId}/waveform")
    public ApiResponse<WaveformResponse> getWaveform(@PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        WaveformResponse response = audioVersionService.getWaveform(versionId, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/version/{versionId}/stream")
    public void streamAudio(@PathVariable Long versionId,
                            HttpServletRequest request,
                            HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        audioVersionService.streamAudio(versionId, request, response, userId);
    }

    @PostMapping("/episode/{episodeId}/final/{versionId}")
    public ApiResponse<AudioVersionResponse> setFinalAudio(@PathVariable Long episodeId,
                                                           @PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AudioVersionResponse response = audioVersionService.setEpisodeFinalAudio(episodeId, versionId, userId);
        return ApiResponse.ok("Final audio set successfully", response);
    }
}
