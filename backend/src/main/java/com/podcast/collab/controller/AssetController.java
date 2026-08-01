package com.podcast.collab.controller;

import com.podcast.collab.dto.asset.AssetResponse;
import com.podcast.collab.dto.asset.AssetUsageRequest;
import com.podcast.collab.dto.asset.AssetUsageResponse;
import com.podcast.collab.dto.asset.CreateAssetRequest;
import com.podcast.collab.dto.asset.UpdateAssetRequest;
import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.entity.enums.AssetType;
import com.podcast.collab.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/team/{teamId}")
    public ApiResponse<AssetResponse> create(@PathVariable Long teamId,
                                             @Valid @RequestBody CreateAssetRequest request) {
        return ApiResponse.ok(assetService.create(teamId, request));
    }

    @GetMapping("/team/{teamId}")
    public ApiResponse<List<AssetResponse>> listByTeam(@PathVariable Long teamId,
                                                       @RequestParam(required = false) AssetType type,
                                                       @RequestParam(required = false) String category) {
        return ApiResponse.ok(assetService.listByTeam(teamId, type, category));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(assetService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateAssetRequest request) {
        return ApiResponse.ok(assetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return ApiResponse.ok("Asset deleted successfully", null);
    }

    @PostMapping("/{id}/usage")
    public ApiResponse<AssetUsageResponse> recordUsage(@PathVariable Long id,
                                                       @Valid @RequestBody AssetUsageRequest request) {
        return ApiResponse.ok(assetService.recordUsage(id, request));
    }

    @GetMapping("/{id}/usages")
    public ApiResponse<List<AssetUsageResponse>> listUsages(@PathVariable Long id) {
        return ApiResponse.ok(assetService.listUsages(id));
    }
}
