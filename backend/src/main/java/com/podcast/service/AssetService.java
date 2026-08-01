package com.podcast.service;

import com.podcast.domain.*;
import com.podcast.repository.AssetRepository;
import com.podcast.repository.AssetUsageRepository;
import com.podcast.repository.EpisodeRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import com.podcast.web.dto.AssetDtos.RecordUsageRequest;
import com.podcast.web.dto.AssetDtos.TextAssetRequest;
import com.podcast.web.dto.AssetDtos.UpdateAssetRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Asset library (README §4.5): categorised audio/text assets with preview and
 * per-episode usage tracking. Team-isolated; any team member may read, and
 * producer-level users manage the library and record usage.
 */
@Service
public class AssetService {

    private final AssetRepository assetRepo;
    private final AssetUsageRepository usageRepo;
    private final EpisodeRepository episodeRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final StorageService storage;
    private final AudioFileValidator validator;
    private final FfmpegService ffmpeg;
    private final SignedUrlService signedUrl;
    private final AuditService audit;

    public AssetService(AssetRepository assetRepo, AssetUsageRepository usageRepo,
                        EpisodeRepository episodeRepo, AccessGuard accessGuard,
                        AuthorizationService authz, StorageService storage,
                        AudioFileValidator validator, FfmpegService ffmpeg,
                        SignedUrlService signedUrl, AuditService audit) {
        this.assetRepo = assetRepo;
        this.usageRepo = usageRepo;
        this.episodeRepo = episodeRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.storage = storage;
        this.validator = validator;
        this.ffmpeg = ffmpeg;
        this.signedUrl = signedUrl;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Asset> list(AssetType type) {
        Long teamId = accessGuard.requireTeamId();
        return type == null
                ? assetRepo.findByTeamIdOrderByCreatedAtDesc(teamId)
                : assetRepo.findByTeamIdAndType(teamId, type);
    }

    private Asset requireAsset(Long assetId) {
        Long teamId = accessGuard.requireTeamId();
        Asset a = assetRepo.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("素材不存在"));
        if (!teamId.equals(a.getTeamId())) {
            throw ApiException.notFound("素材不存在"); // don't leak cross-team
        }
        return a;
    }

    /** Preview URL for audio assets (signed short-lived stream), else null. */
    public String previewUrl(Asset a) {
        if (a.getType() != AssetType.AUDIO || a.getFileKey() == null) {
            return null;
        }
        return "/api/media/assets/" + a.getId() + "?token=" + signedUrl.sign("asset", a.getId());
    }

    @Transactional
    public Asset createText(TextAssetRequest req) {
        Long teamId = accessGuard.requireTeamId();
        authz.checkCanManageTasks(); // producer-level manages the library
        Asset a = new Asset();
        a.setTeamId(teamId);
        a.setName(req.name());
        a.setType(AssetType.TEXT);
        a.setCategory(req.category());
        a.setTextContent(req.textContent());
        a = assetRepo.save(a);
        audit.log("ASSET_CREATE", "Asset", a.getId(), "type=TEXT");
        return a;
    }

    @Transactional
    public Asset uploadAudio(String name, String category, MultipartFile file) {
        Long teamId = accessGuard.requireTeamId();
        authz.checkCanManageTasks();
        String ext = validator.validate(file);
        String storedName = "asset-" + UUID.randomUUID() + "." + ext;
        String key;
        try {
            key = storage.storeAsset(teamId, storedName, file.getInputStream());
        } catch (Exception e) {
            throw ApiException.badRequest("文件读取失败: " + e.getMessage());
        }
        Path path = storage.resolve(key);
        FfmpegService.AudioMeta meta = ffmpeg.probe(path);

        Asset a = new Asset();
        a.setTeamId(teamId);
        a.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        a.setType(AssetType.AUDIO);
        a.setCategory(category);
        a.setFileKey(key);
        a.setFileUrl("/api/media/assets/PENDING");
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setDurationMs(meta.durationMs());
        a = assetRepo.save(a);
        a.setFileUrl("/api/media/assets/" + a.getId());
        assetRepo.save(a);
        audit.log("ASSET_CREATE", "Asset", a.getId(), "type=AUDIO");
        return a;
    }

    @Transactional
    public Asset update(Long assetId, UpdateAssetRequest req) {
        Asset a = requireAsset(assetId);
        authz.checkCanManageTasks();
        if (req.name() != null) a.setName(req.name());
        if (req.category() != null) a.setCategory(req.category());
        if (req.textContent() != null && a.getType() == AssetType.TEXT) {
            a.setTextContent(req.textContent());
        }
        a = assetRepo.save(a);
        audit.log("ASSET_UPDATE", "Asset", a.getId(), null);
        return a;
    }

    @Transactional
    public void delete(Long assetId) {
        Asset a = requireAsset(assetId);
        authz.checkCanManageTasks();
        // Remove usage records first to satisfy FK constraints.
        usageRepo.findByAssetIdOrderByCreatedAtAsc(assetId).forEach(usageRepo::delete);
        assetRepo.delete(a);
        audit.log("ASSET_DELETE", "Asset", assetId, null);
    }

    // ---------- Usage tracking (README §4.5) ----------

    @Transactional
    public AssetUsage recordUsage(Long assetId, RecordUsageRequest req) {
        Asset a = requireAsset(assetId);
        authz.checkCanManageTasks();
        accessGuard.requireEpisode(req.episodeId()); // team isolation on episode
        AssetUsage u = new AssetUsage();
        u.setAssetId(assetId);
        u.setEpisodeId(req.episodeId());
        u.setPositionMs(req.positionMs());
        u.setNote(req.note());
        u.setCreatedBy(SecurityUtils.currentUserId());
        u = usageRepo.save(u);
        // Keep the rollup counter in sync.
        a.setUsageCount((int) usageRepo.countByAssetId(assetId));
        assetRepo.save(a);
        audit.log("ASSET_USAGE_RECORD", "AssetUsage", u.getId(),
                "asset=" + assetId + " episode=" + req.episodeId());
        return u;
    }

    @Transactional(readOnly = true)
    public List<AssetUsage> listUsage(Long assetId) {
        requireAsset(assetId);
        return usageRepo.findByAssetIdOrderByCreatedAtAsc(assetId);
    }

    public Episode episodeOf(Long episodeId) {
        return episodeRepo.findById(episodeId).orElse(null);
    }

    /** Loads an asset for signed-token preview streaming (no session context). */
    @Transactional(readOnly = true)
    public Asset getForStreaming(Long assetId) {
        return assetRepo.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("素材不存在"));
    }
}
