package com.podcast.web;

import com.podcast.service.EpisodeService;
import com.podcast.web.dto.EpisodeDtos.CreateEpisodeRequest;
import com.podcast.web.dto.EpisodeDtos.EpisodeResponse;
import com.podcast.web.dto.EpisodeDtos.UpdateEpisodeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EpisodeController {

    private final EpisodeService service;

    public EpisodeController(EpisodeService service) {
        this.service = service;
    }

    @GetMapping("/podcasts/{podcastId}/episodes")
    public List<EpisodeResponse> list(@PathVariable Long podcastId) {
        return service.listByPodcast(podcastId).stream().map(EpisodeResponse::from).toList();
    }

    @GetMapping("/episodes/{episodeId}")
    public EpisodeResponse get(@PathVariable Long episodeId) {
        return EpisodeResponse.from(service.get(episodeId));
    }

    @PostMapping("/podcasts/{podcastId}/episodes")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public EpisodeResponse create(@PathVariable Long podcastId, @Valid @RequestBody CreateEpisodeRequest req) {
        return EpisodeResponse.from(service.create(podcastId, req));
    }

    @PutMapping("/episodes/{episodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public EpisodeResponse update(@PathVariable Long episodeId, @Valid @RequestBody UpdateEpisodeRequest req) {
        return EpisodeResponse.from(service.update(episodeId, req));
    }

    @DeleteMapping("/episodes/{episodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public ResponseEntity<Void> delete(@PathVariable Long episodeId) {
        service.delete(episodeId);
        return ResponseEntity.noContent().build();
    }
}
