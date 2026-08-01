package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.Asset;
import com.podcast.collab.entity.AssetUsage;
import com.podcast.collab.repository.AssetRepository;
import com.podcast.collab.repository.AssetUsageRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 素材库：音频素材（开场音乐/过渡音效/广告片花）+ 文本素材（口播文案/slogan）+ 使用追踪 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {
    private final AssetRepository assetRepository;
    private final AssetUsageRepository assetUsageRepository;
    private final TeamGuard teamGuard;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @GetMapping("/assets")
    public List<Asset> list(@RequestParam(required = false) String type) {
        Long teamId = SecurityUtils.currentTeamId();
        return type == null ? assetRepository.findByTeamId(teamId)
                : assetRepository.findByTeamIdAndType(teamId, type);
    }

    /** 创建文本素材 */
    @PostMapping("/assets")
    public Asset create(@Valid @RequestBody AssetRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER", "EDITOR", "OPERATOR");
        Asset asset = new Asset();
        asset.setTeamId(SecurityUtils.currentTeamId());
        asset.setName(req.name());
        asset.setType(req.type());
        asset.setCategory(req.category());
        asset.setContent(req.content());
        return assetRepository.save(asset);
    }

    /** 上传音频素材文件 */
    @PostMapping("/assets/{id}/file")
    public Asset uploadFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        Asset asset = requireAsset(id);
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(original.endsWith(".wav") || original.endsWith(".mp3") || original.endsWith(".m4a"))) {
            throw new IllegalArgumentException("仅支持 WAV/MP3/M4A 格式");
        }
        Path dir = Paths.get(uploadDir, "assets", String.valueOf(asset.getTeamId()));
        Files.createDirectories(dir);
        String ext = original.substring(original.lastIndexOf('.'));
        Path target = dir.resolve(UUID.randomUUID() + ext);
        file.transferTo(target);
        asset.setFileUrl(target.toString());
        return assetRepository.save(asset);
    }

    @PutMapping("/assets/{id}")
    public Asset update(@PathVariable Long id, @Valid @RequestBody AssetRequest req) {
        Asset asset = requireAsset(id);
        asset.setName(req.name());
        asset.setType(req.type());
        asset.setCategory(req.category());
        asset.setContent(req.content());
        return assetRepository.save(asset);
    }

    @DeleteMapping("/assets/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        assetRepository.delete(requireAsset(id));
        return Map.of("message", "素材已删除");
    }

    /** 记录素材在某集的使用位置 */
    @PostMapping("/assets/{id}/usages")
    public Map<String, Object> recordUsage(@PathVariable Long id, @Valid @RequestBody AssetUsageRequest req) {
        Asset asset = requireAsset(id);
        teamGuard.requireEpisode(req.episodeId());
        AssetUsage usage = new AssetUsage();
        usage.setAssetId(id);
        usage.setEpisodeId(req.episodeId());
        usage.setPositionMs(req.positionMs());
        assetUsageRepository.save(usage);
        asset.setUsageCount(asset.getUsageCount() + 1);
        assetRepository.save(asset);
        return Map.of("message", "使用记录已保存", "usageCount", asset.getUsageCount());
    }

    /** 素材使用追踪：在每个单集中使用的位置和次数 */
    @GetMapping("/assets/{id}/usages")
    public List<AssetUsage> usages(@PathVariable Long id) {
        requireAsset(id);
        return assetUsageRepository.findByAssetId(id);
    }

    @GetMapping("/episodes/{episodeId}/asset-usages")
    public List<Map<String, Object>> episodeUsages(@PathVariable Long episodeId) {
        teamGuard.requireEpisode(episodeId);
        return assetUsageRepository.findByEpisodeId(episodeId).stream().map(u -> {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("id", u.getId());
            map.put("assetId", u.getAssetId());
            map.put("assetName", assetRepository.findById(u.getAssetId()).map(Asset::getName).orElse("未知"));
            map.put("positionMs", u.getPositionMs());
            map.put("createdAt", u.getCreatedAt());
            return map;
        }).toList();
    }

    private Asset requireAsset(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
        if (!asset.getTeamId().equals(SecurityUtils.currentTeamId())) {
            throw new com.podcast.collab.security.ForbiddenException("无权访问该素材");
        }
        return asset;
    }
}
