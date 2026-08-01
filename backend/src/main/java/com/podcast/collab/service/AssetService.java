package com.podcast.collab.service;

import com.podcast.collab.dto.asset.AssetResponse;
import com.podcast.collab.dto.asset.AssetUsageRequest;
import com.podcast.collab.dto.asset.AssetUsageResponse;
import com.podcast.collab.dto.asset.CreateAssetRequest;
import com.podcast.collab.dto.asset.UpdateAssetRequest;
import com.podcast.collab.entity.Asset;
import com.podcast.collab.entity.AssetUsage;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.AssetType;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AssetRepository;
import com.podcast.collab.repository.AssetUsageRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.UserRepository;
import com.podcast.collab.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetUsageRepository assetUsageRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AssetService(AssetRepository assetRepository,
                        AssetUsageRepository assetUsageRepository,
                        EpisodeRepository episodeRepository,
                        PodcastRepository podcastRepository,
                        TeamMemberRepository teamMemberRepository,
                        UserRepository userRepository,
                        AuditService auditService) {
        this.assetRepository = assetRepository;
        this.assetUsageRepository = assetUsageRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AssetResponse create(Long teamId, CreateAssetRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        verifyTeamMembership(teamId, userId);
        validateAssetFields(request.getType(), request.getFileUrl(), request.getContent());

        Asset asset = Asset.builder()
                .teamId(teamId)
                .name(request.getName())
                .type(request.getType())
                .category(request.getCategory())
                .fileUrl(request.getFileUrl())
                .content(request.getContent())
                .usageCount(0)
                .createdBy(userId)
                .build();
        asset = assetRepository.save(asset);

        auditService.log(userId, "CREATE_ASSET", "Asset", asset.getId(),
                "Asset created: " + asset.getName());

        return mapToResponse(asset);
    }

    @Transactional(readOnly = true)
    public AssetResponse getById(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Asset asset = findAssetOrThrow(id);
        verifyTeamMembership(asset.getTeamId(), userId);
        return mapToResponse(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listByTeam(Long teamId, AssetType type, String category) {
        Long userId = SecurityUtils.getCurrentUserId();
        verifyTeamMembership(teamId, userId);

        List<Asset> assets;
        if (type != null && category != null) {
            assets = assetRepository.findByTeamIdAndType(teamId, type).stream()
                    .filter(a -> category.equals(a.getCategory()))
                    .toList();
        } else if (type != null) {
            assets = assetRepository.findByTeamIdAndType(teamId, type);
        } else if (category != null) {
            assets = assetRepository.findByTeamIdAndCategory(teamId, category);
        } else {
            assets = assetRepository.findByTeamId(teamId);
        }

        return assets.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public AssetResponse update(Long id, UpdateAssetRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Asset asset = findAssetOrThrow(id);
        verifyTeamMembership(asset.getTeamId(), userId);

        if (request.getName() != null) {
            asset.setName(request.getName());
        }
        if (request.getCategory() != null) {
            asset.setCategory(request.getCategory());
        }
        if (request.getFileUrl() != null) {
            asset.setFileUrl(request.getFileUrl());
        }
        if (request.getContent() != null) {
            asset.setContent(request.getContent());
        }

        validateAssetFields(asset.getType(), asset.getFileUrl(), asset.getContent());

        asset = assetRepository.save(asset);

        auditService.log(userId, "UPDATE_ASSET", "Asset", asset.getId(),
                "Asset updated: " + asset.getName());

        return mapToResponse(asset);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Asset asset = findAssetOrThrow(id);
        verifyTeamMembership(asset.getTeamId(), userId);
        assetRepository.delete(asset);

        auditService.log(userId, "DELETE_ASSET", "Asset", id,
                "Asset deleted: " + asset.getName());
    }

    @Transactional
    public AssetUsageResponse recordUsage(Long assetId, AssetUsageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Asset asset = findAssetOrThrow(assetId);
        verifyTeamMembership(asset.getTeamId(), userId);

        Episode episode = episodeRepository.findById(request.getEpisodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        AssetUsage usage = AssetUsage.builder()
                .assetId(assetId)
                .episodeId(request.getEpisodeId())
                .timeMs(request.getTimeMs())
                .build();
        usage = assetUsageRepository.save(usage);

        asset.setUsageCount(asset.getUsageCount() + 1);
        assetRepository.save(asset);

        auditService.log(userId, "RECORD_ASSET_USAGE", "Asset", assetId,
                "Asset used in episode " + request.getEpisodeId());

        return AssetUsageResponse.builder()
                .id(usage.getId())
                .assetId(usage.getAssetId())
                .episodeId(usage.getEpisodeId())
                .timeMs(usage.getTimeMs())
                .usedAt(usage.getUsedAt())
                .assetName(asset.getName())
                .episodeTitle(episode.getTitle())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AssetUsageResponse> listUsages(Long assetId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Asset asset = findAssetOrThrow(assetId);
        verifyTeamMembership(asset.getTeamId(), userId);

        return assetUsageRepository.findByAssetId(assetId).stream()
                .map(usage -> {
                    String episodeTitle = episodeRepository.findById(usage.getEpisodeId())
                            .map(Episode::getTitle)
                            .orElse(null);
                    return AssetUsageResponse.builder()
                            .id(usage.getId())
                            .assetId(usage.getAssetId())
                            .episodeId(usage.getEpisodeId())
                            .timeMs(usage.getTimeMs())
                            .usedAt(usage.getUsedAt())
                            .assetName(asset.getName())
                            .episodeTitle(episodeTitle)
                            .build();
                })
                .toList();
    }

    private void validateAssetFields(AssetType type, String fileUrl, String content) {
        if (type == AssetType.AUDIO && (fileUrl == null || fileUrl.isBlank())) {
            throw new BadRequestException("fileUrl is required for AUDIO type assets");
        }
        if (type == AssetType.TEXT && (content == null || content.isBlank())) {
            throw new BadRequestException("content is required for TEXT type assets");
        }
    }

    private Asset findAssetOrThrow(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyTeamMembership(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private AssetResponse mapToResponse(Asset asset) {
        String createdByName = userRepository.findById(asset.getCreatedBy())
                .map(User::getName)
                .orElse(null);

        return AssetResponse.builder()
                .id(asset.getId())
                .teamId(asset.getTeamId())
                .name(asset.getName())
                .type(asset.getType())
                .category(asset.getCategory())
                .fileUrl(asset.getFileUrl())
                .content(asset.getContent())
                .usageCount(asset.getUsageCount())
                .createdBy(asset.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
