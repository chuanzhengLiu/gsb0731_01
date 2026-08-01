package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.marker.CreateMarkerRequest;
import com.podcast.collab.dto.marker.MarkerFilterRequest;
import com.podcast.collab.dto.marker.MarkerResponse;
import com.podcast.collab.dto.marker.UpdateMarkerRequest;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TimelineMarkerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/markers")
public class MarkerController {

    private final TimelineMarkerService timelineMarkerService;

    public MarkerController(TimelineMarkerService timelineMarkerService) {
        this.timelineMarkerService = timelineMarkerService;
    }

    @PostMapping("/version/{versionId}")
    public ApiResponse<MarkerResponse> create(@PathVariable Long versionId,
                                              @Valid @RequestBody CreateMarkerRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        MarkerResponse response = timelineMarkerService.create(versionId, request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/version/{versionId}")
    public ApiResponse<List<MarkerResponse>> listByVersion(@PathVariable Long versionId,
                                                           MarkerFilterRequest filter) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<MarkerResponse> response = timelineMarkerService.listByVersion(
                versionId,
                filter.getType(),
                filter.getStatus(),
                filter.getCreatedBy(),
                filter.getKeyword(),
                userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<MarkerResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        MarkerResponse response = timelineMarkerService.getById(id, userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<MarkerResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateMarkerRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        MarkerResponse response = timelineMarkerService.update(id, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        timelineMarkerService.delete(id, userId);
        return ApiResponse.ok("Marker deleted successfully", null);
    }

    @GetMapping("/version/{versionId}/stats")
    public ApiResponse<Map<String, Long>> getStats(@PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Long> stats = timelineMarkerService.getMarkerStats(versionId, userId);
        return ApiResponse.ok(stats);
    }
}
