package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.Asset;
import com.podcast.collab.domain.AssetUsage;
import com.podcast.collab.dto.AssetDtos.*;
import com.podcast.collab.repo.AssetRepository;
import com.podcast.collab.repo.AssetUsageRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssetService {

    private final AssetRepository assetRepo;
    private final AssetUsageRepository usageRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;
    private final SignedUrlService signedUrlService;
    private final StorageService storage;

    private static final long MAX_AUDIO_SIZE = 500L * 1024 * 1024;
    private static final Set<String> AUDIO_EXT = Set.of(".wav", ".mp3", ".m4a", ".aac");

    public AssetService(AssetRepository assetRepo, AssetUsageRepository usageRepo, TeamGuard teamGuard,
                        AuditService auditService, SignedUrlService signedUrlService, StorageService storage) {
        this.assetRepo = assetRepo;
        this.usageRepo = usageRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
        this.signedUrlService = signedUrlService;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list(CurrentUser user, com.podcast.collab.domain.Enums.AssetType type) {
        var query = type != null
                ? assetRepo.findByTeamIdAndTypeOrderByCreatedAtDesc(user.teamId(), type)
                : assetRepo.findByTeamIdOrderByCreatedAtDesc(user.teamId());
        return query.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetResponse create(CreateAssetRequest req, CurrentUser user) {
        Asset a = new Asset();
        a.setTeamId(user.teamId());
        a.setName(req.name());
        a.setType(req.type());
        a.setFileUrl(req.fileUrl());
        a.setContent(req.content());
        assetRepo.save(a);
        auditService.log(user, "ASSET_CREATE", "Asset", a.getId(), Map.of("type", req.type().name()));
        return toResponse(a);
    }

    @Transactional
    public AssetResponse uploadAudio(String name, MultipartFile file, CurrentUser user) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_FILE, "No file provided");
        }
        if (file.getSize() > MAX_AUDIO_SIZE) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        String original = file.getOriginalFilename() == null ? "asset" : file.getOriginalFilename().toLowerCase();
        boolean extOk = AUDIO_EXT.stream().anyMatch(original::endsWith);
        if (!extOk) {
            throw new ApiException(ErrorCode.BAD_FILE, "Only WAV, MP3 and M4A files are allowed");
        }
        try {
            StorageService.StoredFile stored = storage.store(
                    file.getInputStream(), "assets", file.getOriginalFilename(), file.getContentType(), MAX_AUDIO_SIZE);
            Asset a = new Asset();
            a.setTeamId(user.teamId());
            a.setName((name == null || name.isBlank()) ? file.getOriginalFilename() : name);
            a.setType(com.podcast.collab.domain.Enums.AssetType.AUDIO);
            a.setFileUrl(storage.toPublicUrl(stored.relativePath()));
            assetRepo.save(a);
            auditService.log(user, "ASSET_UPLOAD", "Asset", a.getId(),
                    Map.of("fileName", file.getOriginalFilename(), "size", file.getSize()));
            return toResponse(a);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to store file: " + e.getMessage());
        }
    }

    @Transactional
    public AssetResponse update(Long id, UpdateAssetRequest req, CurrentUser user) {
        Asset a = requireOwned(id, user);
        if (req.name() != null) a.setName(req.name());
        if (req.fileUrl() != null) a.setFileUrl(req.fileUrl());
        if (req.content() != null) a.setContent(req.content());
        assetRepo.save(a);
        auditService.log(user, "ASSET_UPDATE", "Asset", id, null);
        return toResponse(a);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Asset a = requireOwned(id, user);
        assetRepo.delete(a);
        auditService.log(user, "ASSET_DELETE", "Asset", id, null);
    }

    @Transactional
    public AssetUsageResponse recordUsage(Long assetId, CreateUsageRequest req, CurrentUser user) {
        Asset a = requireOwned(assetId, user);
        teamGuard.requireEpisode(req.episodeId(), user);
        AssetUsage usage = new AssetUsage();
        usage.setAssetId(a.getId());
        usage.setEpisodeId(req.episodeId());
        usage.setUsedAtMs(req.usedAtMs());
        usage.setUsedAt(Instant.now());
        usageRepo.save(usage);
        a.setUsageCount(a.getUsageCount() + 1);
        assetRepo.save(a);
        auditService.log(user, "ASSET_USAGE", "AssetUsage", usage.getId(),
                Map.of("assetId", assetId, "episodeId", req.episodeId()));
        return new AssetUsageResponse(usage.getId(), usage.getAssetId(), usage.getEpisodeId(),
                usage.getUsedAtMs(), usage.getUsedAt());
    }

    @Transactional(readOnly = true)
    public List<AssetUsageResponse> usages(Long assetId, CurrentUser user) {
        requireOwned(assetId, user);
        return usageRepo.findByAssetIdOrderByUsedAtDesc(assetId).stream()
                .map(u -> new AssetUsageResponse(u.getId(), u.getAssetId(), u.getEpisodeId(),
                        u.getUsedAtMs(), u.getUsedAt()))
                .toList();
    }

    private Asset requireOwned(Long id, CurrentUser user) {
        Asset a = assetRepo.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!a.getTeamId().equals(user.teamId())) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        return a;
    }

    private AssetResponse toResponse(Asset a) {
        String fileUrl = a.getFileUrl();
        if (fileUrl != null && a.getType() == com.podcast.collab.domain.Enums.AssetType.AUDIO) {
            fileUrl = signedUrlService.signStream(fileUrl);
        }
        return new AssetResponse(a.getId(), a.getName(), a.getType(), fileUrl, a.getContent(),
                a.getUsageCount(), a.getCreatedAt());
    }
}
