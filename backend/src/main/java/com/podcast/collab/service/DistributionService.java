package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.*;
import com.podcast.collab.dto.DistributionDtos.*;
import com.podcast.collab.repo.*;
import com.podcast.collab.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DistributionService {

    private final DistributionRepository distributionRepo;
    private final PlatformRepository platformRepo;
    private final PlatformAccountRepository accountRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    public DistributionService(DistributionRepository distributionRepo, PlatformRepository platformRepo,
                               PlatformAccountRepository accountRepo, TeamGuard teamGuard,
                               AuditService auditService) {
        this.distributionRepo = distributionRepo;
        this.platformRepo = platformRepo;
        this.accountRepo = accountRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlatformResponse> listPlatforms() {
        return platformRepo.findAll().stream()
                .map(p -> new PlatformResponse(p.getId(), p.getName(), p.getCode(),
                        p.getRssRequiredFieldsJson(), p.getCategoryOptionsJson()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformAccountResponse> listAccounts(CurrentUser user) {
        return accountRepo.findByTeamIdOrderByCreatedAtDesc(user.teamId()).stream()
                .map(a -> {
                    String name = platformRepo.findById(a.getPlatformId()).map(Platform::getName).orElse("");
                    return new PlatformAccountResponse(a.getId(), a.getPlatformId(), name,
                            a.getDisplayName(), a.getCreatedAt());
                }).toList();
    }

    @Transactional
    public PlatformAccountResponse createAccount(CreateAccountRequest req, CurrentUser user) {
        requireOperator(user);
        Platform platform = platformRepo.findById(req.platformId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Platform not found"));
        PlatformAccount acc = new PlatformAccount();
        acc.setTeamId(user.teamId());
        acc.setPlatformId(platform.getId());
        acc.setDisplayName(req.displayName());
        acc.setCredentialsJson(req.credentialsJson());
        accountRepo.save(acc);
        auditService.log(user, "PLATFORM_ACCOUNT_CREATE", "PlatformAccount", acc.getId(),
                Map.of("platformId", platform.getId()));
        return new PlatformAccountResponse(acc.getId(), platform.getId(), platform.getName(),
                acc.getDisplayName(), acc.getCreatedAt());
    }

    @Transactional
    public void deleteAccount(Long accountId, CurrentUser user) {
        requireOperator(user);
        PlatformAccount acc = accountRepo.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!acc.getTeamId().equals(user.teamId())) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        accountRepo.delete(acc);
        auditService.log(user, "PLATFORM_ACCOUNT_DELETE", "PlatformAccount", accountId, null);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> listByEpisode(Long episodeId, CurrentUser user) {
        teamGuard.requireEpisode(episodeId, user);
        return distributionRepo.findByEpisodeIdOrderByCreatedAtDesc(episodeId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public DistributionResponse create(Long episodeId, CreateDistributionRequest req, CurrentUser user) {
        requireOperator(user);
        teamGuard.requireEpisode(episodeId, user);
        PlatformAccount acc = accountRepo.findById(req.platformAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Platform account not found"));
        if (!acc.getTeamId().equals(user.teamId())) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        distributionRepo.findByEpisodeIdAndPlatformAccountId(episodeId, acc.getId()).ifPresent(d -> {
            throw new ApiException(ErrorCode.CONFLICT, "Distribution for this platform already exists");
        });
        Distribution d = new Distribution();
        d.setEpisodeId(episodeId);
        d.setPlatformAccountId(acc.getId());
        d.setStatus(Enums.DistributionStatus.NOT_STARTED);
        d.setPlatformDataJson(req.platformDataJson());
        distributionRepo.save(d);
        auditService.log(user, "DISTRIBUTION_CREATE", "Distribution", d.getId(),
                Map.of("episodeId", episodeId, "accountId", acc.getId()));
        return toResponse(d);
    }

    @Transactional
    public DistributionResponse update(Long distributionId, UpdateDistributionRequest req, CurrentUser user) {
        requireOperator(user);
        Distribution d = distributionRepo.findById(distributionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(d.getEpisodeId(), user);
        if (req.status() != null) {
            d.setStatus(req.status());
            if (req.status() == Enums.DistributionStatus.SUBMITTED && d.getSubmittedAt() == null) {
                d.setSubmittedAt(Instant.now());
            }
            if (req.status() == Enums.DistributionStatus.PUBLISHED && d.getPublishedAt() == null) {
                d.setPublishedAt(Instant.now());
            }
        }
        if (req.platformDataJson() != null) d.setPlatformDataJson(req.platformDataJson());
        if (req.rejectionReason() != null) d.setRejectionReason(req.rejectionReason());
        distributionRepo.save(d);
        auditService.log(user, "DISTRIBUTION_UPDATE", "Distribution", d.getId(),
                Map.of("status", d.getStatus().name()));
        return toResponse(d);
    }

    @Transactional
    public void delete(Long distributionId, CurrentUser user) {
        requireOperator(user);
        Distribution d = distributionRepo.findById(distributionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(d.getEpisodeId(), user);
        distributionRepo.delete(d);
        auditService.log(user, "DISTRIBUTION_DELETE", "Distribution", distributionId, null);
    }

    private void requireOperator(CurrentUser user) {
        if (!user.isOperator()) {
            throw new AccessDeniedException("Operator role required");
        }
    }

    private DistributionResponse toResponse(Distribution d) {
        PlatformAccount acc = accountRepo.findById(d.getPlatformAccountId()).orElse(null);
        String platformName = acc != null
                ? platformRepo.findById(acc.getPlatformId()).map(Platform::getName).orElse("") : "";
        return new DistributionResponse(d.getId(), d.getEpisodeId(), d.getPlatformAccountId(),
                platformName, acc != null ? acc.getDisplayName() : "", d.getStatus(),
                d.getSubmittedAt(), d.getPublishedAt(), d.getPlatformDataJson(),
                d.getRejectionReason(), d.getUpdatedAt());
    }
}
