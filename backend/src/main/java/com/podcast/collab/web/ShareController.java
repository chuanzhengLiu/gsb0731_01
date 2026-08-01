package com.podcast.collab.web;

import com.podcast.collab.domain.AudioVersion;
import com.podcast.collab.domain.Episode;
import com.podcast.collab.domain.Podcast;
import com.podcast.collab.dto.StatsDtos.ShareLinkResponse;
import com.podcast.collab.repo.AudioVersionRepository;
import com.podcast.collab.repo.EpisodeRepository;
import com.podcast.collab.repo.PodcastRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.ShareService;
import com.podcast.collab.service.SignedUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ShareController {

    private final ShareService shareService;
    private final EpisodeRepository episodeRepo;
    private final PodcastRepository podcastRepo;
    private final AudioVersionRepository versionRepo;
    private final SignedUrlService signedUrlService;

    public ShareController(ShareService shareService, EpisodeRepository episodeRepo,
                           PodcastRepository podcastRepo, AudioVersionRepository versionRepo,
                           SignedUrlService signedUrlService) {
        this.shareService = shareService;
        this.episodeRepo = episodeRepo;
        this.podcastRepo = podcastRepo;
        this.versionRepo = versionRepo;
        this.signedUrlService = signedUrlService;
    }

    @PostMapping("/episodes/{episodeId}/share")
    public ShareLinkResponse create(@PathVariable Long episodeId) {
        return shareService.create(episodeId, SecurityUtils.requireUser());
    }

    @DeleteMapping("/share-links/{linkId}")
    public void revoke(@PathVariable Long linkId) {
        shareService.revoke(linkId, SecurityUtils.requireUser());
    }

    @GetMapping("/share/{token}")
    public Map<String, Object> view(@PathVariable String token, HttpServletRequest req) {
        var link = shareService.verify(token, req);
        Episode episode = episodeRepo.findById(link.getEpisodeId()).orElseThrow();
        Podcast podcast = podcastRepo.findById(episode.getPodcastId()).orElseThrow();
        // Guests get the latest non-archived version; the signed URL is their authorization.
        AudioVersion latest = versionRepo.findByEpisodeIdOrderByVersionNumberDesc(episode.getId()).stream()
                .filter(v -> !v.isArchived())
                .findFirst()
                .orElse(null);

        // Use LinkedHashMap to preserve key order and allow null values (unlike Map.of).
        Map<String, Object> versionPayload = null;
        if (latest != null) {
            versionPayload = new LinkedHashMap<>();
            versionPayload.put("id", latest.getId());
            versionPayload.put("versionNumber", latest.getVersionNumber());
            versionPayload.put("fileUrl", signedUrlService.signStream(latest.getFileUrl()));
            versionPayload.put("durationMs", latest.getDurationMs());
            versionPayload.put("peaksUrl", latest.getPeaksUrl() == null
                    ? "" : signedUrlService.signStream(latest.getPeaksUrl()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("episode", Map.of(
                "id", episode.getId(),
                "title", episode.getTitle(),
                "theme", episode.getTheme() == null ? "" : episode.getTheme(),
                "status", episode.getStatus().toString(),
                "number", episode.getNumber()));
        body.put("podcast", Map.of("id", podcast.getId(), "name", podcast.getName()));
        body.put("latestVersion", versionPayload);
        body.put("expiresAt", link.getExpiresAt().toString());
        return body;
    }
}
