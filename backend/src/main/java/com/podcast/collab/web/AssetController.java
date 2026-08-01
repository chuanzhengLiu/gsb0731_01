package com.podcast.collab.web;

import com.podcast.collab.domain.Enums;
import com.podcast.collab.dto.AssetDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public List<AssetResponse> list(@RequestParam(required = false) Enums.AssetType type) {
        return assetService.list(SecurityUtils.requireUser(), type);
    }

    @PostMapping
    public AssetResponse create(@Valid @RequestBody CreateAssetRequest req) {
        return assetService.create(req, SecurityUtils.requireUser());
    }

    @PostMapping(value = "/audio", consumes = "multipart/form-data")
    public AssetResponse uploadAudio(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "name", required = false) String name) {
        return assetService.uploadAudio(name, file, SecurityUtils.requireUser());
    }

    @PatchMapping("/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAssetRequest req) {
        return assetService.update(id, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        assetService.delete(id, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/usages")
    public List<AssetUsageResponse> usages(@PathVariable Long id) {
        return assetService.usages(id, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/usages")
    public AssetUsageResponse addUsage(@PathVariable Long id, @Valid @RequestBody CreateUsageRequest req) {
        return assetService.recordUsage(id, req, SecurityUtils.requireUser());
    }
}
