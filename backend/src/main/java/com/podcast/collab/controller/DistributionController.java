package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.distribution.CreateDistributionRequest;
import com.podcast.collab.dto.distribution.DistributionResponse;
import com.podcast.collab.dto.distribution.PlatformResponse;
import com.podcast.collab.dto.distribution.UpdateDistributionRequest;
import com.podcast.collab.dto.distribution.UpdateDistributionStatusRequest;
import com.podcast.collab.service.DistributionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/distributions")
public class DistributionController {

    private final DistributionService distributionService;

    public DistributionController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @GetMapping("/platforms")
    public ApiResponse<List<PlatformResponse>> listPlatforms() {
        return ApiResponse.ok(distributionService.listPlatforms());
    }

    @PostMapping("/episode/{episodeId}")
    public ApiResponse<DistributionResponse> createDistribution(@PathVariable Long episodeId,
                                                                @Valid @RequestBody CreateDistributionRequest request) {
        return ApiResponse.ok(distributionService.createDistribution(episodeId, request));
    }

    @GetMapping("/episode/{episodeId}")
    public ApiResponse<List<DistributionResponse>> listByEpisode(@PathVariable Long episodeId) {
        return ApiResponse.ok(distributionService.listByEpisode(episodeId));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DistributionResponse> updateStatus(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateDistributionStatusRequest request) {
        return ApiResponse.ok(distributionService.updateStatus(id, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DistributionResponse> updateData(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateDistributionRequest request) {
        return ApiResponse.ok(distributionService.updateData(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        distributionService.delete(id);
        return ApiResponse.ok("Distribution deleted successfully", null);
    }

    @GetMapping(value = "/podcast/{podcastId}/rss", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> generateRssFeed(@PathVariable Long podcastId) {
        String rss = distributionService.generateRssFeed(podcastId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        return new ResponseEntity<>(rss, headers, org.springframework.http.HttpStatus.OK);
    }
}
