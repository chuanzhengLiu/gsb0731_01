package com.podcast.service;

import com.podcast.domain.*;
import com.podcast.repository.*;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import com.podcast.web.dto.DistributionDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Distribution management (README §4.4): per-episode, per-platform status
 * tracking, platform account maintenance and publish-calendar queries.
 * Writes are gated to OPERATOR/producer-level (README §9 运营只能操作分发).
 */
@Service
public class DistributionService {

    private final DistributionRepository distRepo;
    private final PlatformRepository platformRepo;
    private final PlatformAccountRepository accountRepo;
    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final AuditService audit;

    public DistributionService(DistributionRepository distRepo, PlatformRepository platformRepo,
                               PlatformAccountRepository accountRepo, PodcastRepository podcastRepo,
                               EpisodeRepository episodeRepo, AccessGuard accessGuard,
                               AuthorizationService authz, AuditService audit) {
        this.distRepo = distRepo;
        this.platformRepo = platformRepo;
        this.accountRepo = accountRepo;
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.audit = audit;
    }

    private String platformName(Long platformId) {
        return platformRepo.findById(platformId).map(Platform::getName).orElse("未知平台");
    }

    // ---------- Platforms (read-only catalogue) ----------

    @Transactional(readOnly = true)
    public List<Platform> listPlatforms() {
        accessGuard.requireTeamId(); // any team member may view
        return platformRepo.findAll();
    }

    // ---------- Platform accounts (README §4.4 平台账号管理) ----------

    @Transactional(readOnly = true)
    public List<PlatformAccount> listAccounts() {
        return accountRepo.findByTeamId(accessGuard.requireTeamId());
    }

    @Transactional
    public PlatformAccount upsertAccount(UpsertAccountRequest req) {
        Long teamId = accessGuard.requireTeamId();
        authz.checkCanManageDistribution();
        platformRepo.findById(req.platformId())
                .orElseThrow(() -> ApiException.badRequest("平台不存在"));
        PlatformAccount a = accountRepo.findByTeamIdAndPlatformId(teamId, req.platformId())
                .orElseGet(PlatformAccount::new);
        a.setTeamId(teamId);
        a.setPlatformId(req.platformId());
        a.setAccountName(req.accountName());
        a.setAccountUrl(req.accountUrl());
        a.setNotes(req.notes());
        a = accountRepo.save(a);
        audit.log("PLATFORM_ACCOUNT_UPSERT", "PlatformAccount", a.getId(),
                "platform=" + req.platformId());
        return a;
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        Long teamId = accessGuard.requireTeamId();
        authz.checkCanManageDistribution();
        PlatformAccount a = accountRepo.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("平台账号不存在"));
        if (!teamId.equals(a.getTeamId())) {
            throw ApiException.notFound("平台账号不存在"); // don't leak cross-team
        }
        accountRepo.delete(a);
        audit.log("PLATFORM_ACCOUNT_DELETE", "PlatformAccount", accountId, null);
    }

    public String accountPlatformName(PlatformAccount a) {
        return platformName(a.getPlatformId());
    }

    // ---------- Per-episode distribution ----------

    @Transactional(readOnly = true)
    public List<Distribution> listForEpisode(Long episodeId) {
        accessGuard.requireEpisode(episodeId); // team isolation
        return distRepo.findByEpisodeId(episodeId);
    }

    public String distributionPlatformName(Distribution d) {
        return platformName(d.getPlatformId());
    }

    /** Selects a platform for an episode (README §4.4 选择要分发的平台) or edits its info. */
    @Transactional
    public Distribution upsertDistribution(Long episodeId, UpsertDistributionRequest req) {
        accessGuard.requireEpisode(episodeId);
        authz.checkCanManageDistribution();
        platformRepo.findById(req.platformId())
                .orElseThrow(() -> ApiException.badRequest("平台不存在"));
        Distribution d = distRepo.findByEpisodeIdAndPlatformId(episodeId, req.platformId())
                .orElseGet(Distribution::new);
        boolean isNew = d.getId() == null;
        d.setEpisodeId(episodeId);
        d.setPlatformId(req.platformId());
        if (isNew) {
            d.setStatus(DistributionStatus.NOT_STARTED);
        }
        if (req.platformDataJson() != null) {
            d.setPlatformDataJson(req.platformDataJson());
        }
        d = distRepo.save(d);
        audit.log(isNew ? "DISTRIBUTION_CREATE" : "DISTRIBUTION_UPDATE",
                "Distribution", d.getId(), "episode=" + episodeId + " platform=" + req.platformId());
        return d;
    }

    @Transactional
    public Distribution changeStatus(Long distributionId, DistributionStatus status) {
        Distribution d = distRepo.findById(distributionId)
                .orElseThrow(() -> ApiException.notFound("分发任务不存在"));
        accessGuard.requireEpisode(d.getEpisodeId()); // team isolation
        authz.checkCanManageDistribution();
        DistributionStatus from = d.getStatus();
        d.setStatus(status);
        // Stamp lifecycle timestamps used by stats (avg review time / 上架率).
        if (status == DistributionStatus.SUBMITTED && d.getSubmittedAt() == null) {
            d.setSubmittedAt(Instant.now());
        }
        if (status == DistributionStatus.PUBLISHED && d.getPublishedAt() == null) {
            d.setPublishedAt(Instant.now());
        }
        d = distRepo.save(d);
        audit.log("DISTRIBUTION_STATUS_CHANGE", "Distribution", d.getId(), from + " -> " + status);
        return d;
    }

    @Transactional
    public void deleteDistribution(Long distributionId) {
        Distribution d = distRepo.findById(distributionId)
                .orElseThrow(() -> ApiException.notFound("分发任务不存在"));
        accessGuard.requireEpisode(d.getEpisodeId());
        authz.checkCanManageDistribution();
        distRepo.delete(d);
        audit.log("DISTRIBUTION_DELETE", "Distribution", distributionId, null);
    }

    // ---------- Publish calendar (README §4.4 发布日历) ----------

    /**
     * Team-wide publish calendar: every episode that has a scheduled/known date
     * with its aggregate distribution status. Uses record_date as the planned
     * date (published episodes surface their published state).
     */
    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar() {
        Long teamId = accessGuard.requireTeamId();
        List<Podcast> podcasts = podcastRepo.findByTeamId(teamId);
        if (podcasts.isEmpty()) {
            return List.of();
        }
        Map<Long, Podcast> podcastById = podcasts.stream()
                .collect(Collectors.toMap(Podcast::getId, p -> p));
        List<Episode> episodes = episodeRepo.findByPodcastIdIn(new ArrayList<>(podcastById.keySet()));

        List<CalendarEntry> entries = new ArrayList<>();
        for (Episode e : episodes) {
            if (e.getRecordDate() == null) {
                continue; // no scheduled date to place on the calendar
            }
            List<Distribution> dists = distRepo.findByEpisodeId(e.getId());
            String status = aggregateStatus(dists, e.getStatus());
            Podcast p = podcastById.get(e.getPodcastId());
            entries.add(new CalendarEntry(
                    e.getId(), e.getNumber(), e.getTitle(),
                    e.getPodcastId(), p != null ? p.getName() : null,
                    e.getRecordDate().toString(), status));
        }
        entries.sort((a, b) -> a.date().compareTo(b.date()));
        return entries;
    }

    private String aggregateStatus(List<Distribution> dists, EpisodeStatus episodeStatus) {
        if (dists.isEmpty()) {
            return episodeStatus.name();
        }
        boolean allPublished = dists.stream()
                .allMatch(d -> d.getStatus() == DistributionStatus.PUBLISHED);
        if (allPublished) return DistributionStatus.PUBLISHED.name();
        boolean anySubmitted = dists.stream()
                .anyMatch(d -> d.getStatus() != DistributionStatus.NOT_STARTED);
        return anySubmitted ? "PARTIAL" : DistributionStatus.NOT_STARTED.name();
    }
}
