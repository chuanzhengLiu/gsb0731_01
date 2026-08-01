package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.Episode;
import com.podcast.collab.domain.Podcast;
import com.podcast.collab.dto.PodcastDtos.*;
import com.podcast.collab.repo.EpisodeRepository;
import com.podcast.collab.repo.PodcastRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PodcastService {

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    public PodcastService(PodcastRepository podcastRepo, EpisodeRepository episodeRepo,
                          TeamGuard teamGuard, AuditService auditService) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PodcastResponse> list(CurrentUser user) {
        return podcastRepo.findByTeamIdOrderByCreatedAtDesc(user.teamId()).stream().map(this::toPodcastResponse).toList();
    }

    @Transactional(readOnly = true)
    public PodcastResponse get(Long id, CurrentUser user) {
        return toPodcastResponse(teamGuard.requirePodcast(id, user));
    }

    @Transactional
    public PodcastResponse create(CreatePodcastRequest req, CurrentUser user) {
        requireProducer(user);
        Podcast p = new Podcast();
        p.setTeamId(user.teamId());
        p.setName(req.name());
        p.setType(req.type());
        p.setUpdateFrequency(req.updateFrequency());
        p.setTargetDurationSeconds(req.targetDurationSeconds());
        p.setStructureTemplateJson(req.structureTemplateJson());
        podcastRepo.save(p);
        auditService.log(user, "PODCAST_CREATE", "Podcast", p.getId(), Map.of("name", p.getName()));
        return toPodcastResponse(p);
    }

    @Transactional
    public PodcastResponse update(Long id, UpdatePodcastRequest req, CurrentUser user) {
        requireProducer(user);
        Podcast p = teamGuard.requirePodcast(id, user);
        if (req.name() != null) p.setName(req.name());
        if (req.type() != null) p.setType(req.type());
        if (req.updateFrequency() != null) p.setUpdateFrequency(req.updateFrequency());
        if (req.targetDurationSeconds() != null) p.setTargetDurationSeconds(req.targetDurationSeconds());
        if (req.structureTemplateJson() != null) p.setStructureTemplateJson(req.structureTemplateJson());
        podcastRepo.save(p);
        auditService.log(user, "PODCAST_UPDATE", "Podcast", p.getId(), null);
        return toPodcastResponse(p);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        requireProducer(user);
        Podcast p = teamGuard.requirePodcast(id, user);
        podcastRepo.delete(p);
        auditService.log(user, "PODCAST_DELETE", "Podcast", id, null);
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listEpisodes(Long podcastId, CurrentUser user) {
        teamGuard.requirePodcast(podcastId, user);
        return episodeRepo.findByPodcastIdOrderByNumberDesc(podcastId).stream().map(this::toEpisodeResponse).toList();
    }

    @Transactional(readOnly = true)
    public EpisodeResponse getEpisode(Long episodeId, CurrentUser user) {
        return toEpisodeResponse(teamGuard.requireEpisode(episodeId, user));
    }

    @Transactional
    public EpisodeResponse createEpisode(Long podcastId, CreateEpisodeRequest req, CurrentUser user) {
        requireProducer(user);
        Podcast p = teamGuard.requirePodcast(podcastId, user);
        Episode e = new Episode();
        e.setPodcastId(p.getId());
        e.setNumber((int) episodeRepo.countByPodcastId(p.getId()) + 1);
        e.setTitle(req.title());
        e.setTheme(req.theme());
        e.setRecordDate(req.recordDate());
        e.setScheduledAt(req.scheduledAt());
        e.setStatus(com.podcast.collab.domain.Enums.EpisodeStatus.PLANNING);
        episodeRepo.save(e);
        auditService.log(user, "EPISODE_CREATE", "Episode", e.getId(), Map.of("podcastId", podcastId));
        return toEpisodeResponse(e);
    }

    @Transactional
    public EpisodeResponse updateEpisode(Long episodeId, UpdateEpisodeRequest req, CurrentUser user) {
        requireProducer(user);
        Episode e = teamGuard.requireEpisode(episodeId, user);
        if (req.title() != null) e.setTitle(req.title());
        if (req.theme() != null) e.setTheme(req.theme());
        if (req.recordDate() != null) e.setRecordDate(req.recordDate());
        if (req.status() != null) e.setStatus(req.status());
        if (req.scheduledAt() != null) e.setScheduledAt(req.scheduledAt());
        episodeRepo.save(e);
        auditService.log(user, "EPISODE_UPDATE", "Episode", e.getId(), Map.of("status", e.getStatus()));
        return toEpisodeResponse(e);
    }

    @Transactional
    public void deleteEpisode(Long episodeId, CurrentUser user) {
        requireProducer(user);
        Episode e = teamGuard.requireEpisode(episodeId, user);
        episodeRepo.delete(e);
        auditService.log(user, "EPISODE_DELETE", "Episode", episodeId, null);
    }

    private void requireProducer(CurrentUser user) {
        if (!user.isProducerOrAbove()) {
            throw new AccessDeniedException("Producer or admin role required");
        }
    }

    private PodcastResponse toPodcastResponse(Podcast p) {
        return new PodcastResponse(p.getId(), p.getName(), p.getType(), p.getUpdateFrequency(),
                p.getTargetDurationSeconds(), p.getStructureTemplateJson(), p.getCreatedAt());
    }

    private EpisodeResponse toEpisodeResponse(Episode e) {
        return new EpisodeResponse(e.getId(), e.getPodcastId(), e.getNumber(), e.getTitle(), e.getTheme(),
                e.getRecordDate(), e.getStatus(), e.getFinalAudioUrl(), e.getScheduledAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
