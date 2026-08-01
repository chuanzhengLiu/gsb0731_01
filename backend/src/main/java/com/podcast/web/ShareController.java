package com.podcast.web;

import com.podcast.service.ShareService;
import com.podcast.service.ShareService.CreatedShare;
import com.podcast.web.dto.ShareDtos.ShareLinkResponse;
import com.podcast.web.dto.ShareDtos.SharedEpisodeView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Guest share links (README §3.1/§8). Management endpoints are authenticated
 * (producer-level); the public view endpoint under /api/share/** is whitelisted
 * and validated purely by token.
 */
@RestController
@RequestMapping("/api")
public class ShareController {

    private final ShareService service;

    public ShareController(ShareService service) {
        this.service = service;
    }

    /** Create a 7-day share link for an episode; returns the openable URL once. */
    @PostMapping("/episodes/{episodeId}/share-links")
    public ShareLinkResponse create(@PathVariable Long episodeId) {
        CreatedShare created = service.create(episodeId);
        return ShareLinkResponse.from(created.link(),
                service.urlFor(created.rawToken()), false);
    }

    @GetMapping("/episodes/{episodeId}/share-links")
    public List<ShareLinkResponse> list(@PathVariable Long episodeId) {
        return service.listForEpisode(episodeId).stream()
                .map(s -> ShareLinkResponse.from(s, service.maskedUrl(s), service.isExpired(s)))
                .toList();
    }

    @DeleteMapping("/share-links/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }

    /** Public guest view (whitelisted). Validated by token; logs the access. */
    @GetMapping("/share/{token}")
    public SharedEpisodeView view(@PathVariable String token, HttpServletRequest http) {
        return service.viewShared(token, http);
    }
}
