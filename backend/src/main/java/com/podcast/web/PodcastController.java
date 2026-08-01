package com.podcast.web;

import com.podcast.service.PodcastService;
import com.podcast.web.dto.PodcastDtos.CreatePodcastRequest;
import com.podcast.web.dto.PodcastDtos.PodcastResponse;
import com.podcast.web.dto.PodcastDtos.UpdatePodcastRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/podcasts")
public class PodcastController {

    private final PodcastService service;

    public PodcastController(PodcastService service) {
        this.service = service;
    }

    @GetMapping
    public List<PodcastResponse> list() {
        return service.list().stream().map(PodcastResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PodcastResponse get(@PathVariable Long id) {
        return PodcastResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public PodcastResponse create(@Valid @RequestBody CreatePodcastRequest req) {
        return PodcastResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public PodcastResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePodcastRequest req) {
        return PodcastResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
