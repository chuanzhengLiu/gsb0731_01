package com.podcast.collab.web;

import com.podcast.collab.dto.DistributionDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.DistributionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DistributionController {

    private final DistributionService service;

    public DistributionController(DistributionService service) {
        this.service = service;
    }

    @GetMapping("/platforms")
    public List<PlatformResponse> platforms() {
        return service.listPlatforms();
    }

    @GetMapping("/platform-accounts")
    public List<PlatformAccountResponse> accounts() {
        return service.listAccounts(SecurityUtils.requireUser());
    }

    @PostMapping("/platform-accounts")
    public PlatformAccountResponse createAccount(@Valid @RequestBody CreateAccountRequest req) {
        return service.createAccount(req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/platform-accounts/{id}")
    public void deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id, SecurityUtils.requireUser());
    }

    @GetMapping("/episodes/{episodeId}/distributions")
    public List<DistributionResponse> list(@PathVariable Long episodeId) {
        return service.listByEpisode(episodeId, SecurityUtils.requireUser());
    }

    @PostMapping("/episodes/{episodeId}/distributions")
    public DistributionResponse create(@PathVariable Long episodeId, @Valid @RequestBody CreateDistributionRequest req) {
        return service.create(episodeId, req, SecurityUtils.requireUser());
    }

    @PatchMapping("/distributions/{id}")
    public DistributionResponse update(@PathVariable Long id, @RequestBody UpdateDistributionRequest req) {
        return service.update(id, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/distributions/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.requireUser());
    }
}
