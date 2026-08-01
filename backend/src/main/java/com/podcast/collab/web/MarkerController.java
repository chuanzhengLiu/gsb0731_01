package com.podcast.collab.web;

import com.podcast.collab.domain.Enums;
import com.podcast.collab.dto.AudioDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.MarkerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audio-versions/{versionId}/markers")
public class MarkerController {

    private final MarkerService markerService;

    public MarkerController(MarkerService markerService) {
        this.markerService = markerService;
    }

    @GetMapping
    public List<MarkerResponse> list(@PathVariable Long versionId,
                                     @RequestParam(required = false) Enums.MarkerType type,
                                     @RequestParam(required = false) Enums.MarkerStatus status,
                                     @RequestParam(required = false) Long createdBy,
                                     @RequestParam(required = false) String keyword) {
        return markerService.list(versionId, type, status, createdBy, keyword, SecurityUtils.requireUser());
    }

    @PostMapping
    public MarkerResponse create(@PathVariable Long versionId, @Valid @RequestBody CreateMarkerRequest req) {
        return markerService.create(versionId, req, SecurityUtils.requireUser());
    }

    @PatchMapping("/{markerId}")
    public MarkerResponse update(@PathVariable Long versionId, @PathVariable Long markerId,
                                 @Valid @RequestBody UpdateMarkerRequest req) {
        return markerService.update(versionId, markerId, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/{markerId}")
    public void delete(@PathVariable Long versionId, @PathVariable Long markerId) {
        markerService.delete(versionId, markerId, SecurityUtils.requireUser());
    }
}
