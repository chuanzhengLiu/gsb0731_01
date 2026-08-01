package com.podcast.collab.web;

import com.podcast.collab.dto.AudioDtos.AudioVersionResponse;
import com.podcast.collab.security.CurrentUser;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AudioService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/episodes/{episodeId}/audio")
public class AudioController {

    private final AudioService audioService;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public AudioVersionResponse upload(@PathVariable Long episodeId,
                                       @RequestParam("file") MultipartFile file) {
        return audioService.upload(episodeId, file, SecurityUtils.requireUser());
    }

    @GetMapping
    public List<AudioVersionResponse> versions(@PathVariable Long episodeId) {
        return audioService.listVersions(episodeId, SecurityUtils.requireUser());
    }

    @PostMapping("/{versionId}/final")
    public Map<String, Object> setFinal(@PathVariable Long episodeId, @PathVariable Long versionId) {
        audioService.setFinal(episodeId, versionId, SecurityUtils.requireUser());
        return Map.of("success", true);
    }

    @PostMapping("/{versionId}/archive")
    public Map<String, Object> archive(@PathVariable Long episodeId, @PathVariable Long versionId) {
        CurrentUser user = SecurityUtils.requireUser();
        audioService.archiveVersion(versionId, user);
        return Map.of("success", true);
    }
}
