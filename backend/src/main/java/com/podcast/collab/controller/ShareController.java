package com.podcast.collab.controller;

import com.podcast.collab.dto.audio.WaveformResponse;
import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.share.CreateShareLinkRequest;
import com.podcast.collab.dto.share.ShareAccessLogResponse;
import com.podcast.collab.dto.share.ShareLinkResponse;
import com.podcast.collab.dto.share.SharedEpisodeResponse;
import com.podcast.collab.service.AudioVersionService;
import com.podcast.collab.service.ShareLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareLinkService shareLinkService;
    private final AudioVersionService audioVersionService;

    public ShareController(ShareLinkService shareLinkService, AudioVersionService audioVersionService) {
        this.shareLinkService = shareLinkService;
        this.audioVersionService = audioVersionService;
    }

    @PostMapping("/create")
    public ApiResponse<ShareLinkResponse> createShareLink(@Valid @RequestBody CreateShareLinkRequest request) {
        return ApiResponse.ok(shareLinkService.createShareLink(request));
    }

    @GetMapping("/episode/{episodeId}")
    public ApiResponse<List<ShareLinkResponse>> listByEpisode(@PathVariable Long episodeId) {
        return ApiResponse.ok(shareLinkService.listByEpisode(episodeId));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<ShareLinkResponse> revoke(@PathVariable Long id) {
        return ApiResponse.ok(shareLinkService.revoke(id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<ShareAccessLogResponse>> getAccessLogs(@PathVariable Long id) {
        return ApiResponse.ok(shareLinkService.getAccessLogs(id));
    }

    @GetMapping("/token/{token}")
    public ApiResponse<SharedEpisodeResponse> getByToken(@PathVariable String token,
                                                         HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return ApiResponse.ok(shareLinkService.getByToken(token, ipAddress, userAgent));
    }

    @GetMapping("/token/{token}/audio")
    public void streamSharedAudio(@PathVariable String token,
                                  @RequestParam Long versionId,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        shareLinkService.validateShareTokenForVersion(token, versionId, ipAddress, userAgent);
        audioVersionService.streamAudioForShare(versionId, request, response);
    }

    @GetMapping("/token/{token}/waveform")
    public ApiResponse<WaveformResponse> getSharedWaveform(@PathVariable String token,
                                                            @RequestParam Long versionId,
                                                            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        shareLinkService.validateShareTokenForVersion(token, versionId, ipAddress, userAgent);
        return ApiResponse.ok(audioVersionService.getWaveformForShare(versionId));
    }
}
