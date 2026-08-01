package com.podcast.service;

import com.podcast.domain.Episode;
import com.podcast.domain.EpisodeStatus;
import com.podcast.repository.EpisodeRepository;
import com.podcast.web.ApiException;
import com.podcast.web.dto.EpisodeDtos.CreateEpisodeRequest;
import com.podcast.web.dto.EpisodeDtos.UpdateEpisodeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EpisodeService {

    private final EpisodeRepository episodeRepo;
    private final AccessGuard accessGuard;
    private final AuditService auditService;

    public EpisodeService(EpisodeRepository episodeRepo, AccessGuard accessGuard, AuditService auditService) {
        this.episodeRepo = episodeRepo;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Episode> listByPodcast(Long podcastId) {
        accessGuard.requirePodcast(podcastId); // enforces team isolation
        return episodeRepo.findByPodcastId(podcastId);
    }

    @Transactional(readOnly = true)
    public Episode get(Long episodeId) {
        return accessGuard.requireEpisode(episodeId);
    }

    @Transactional
    public Episode create(Long podcastId, CreateEpisodeRequest req) {
        accessGuard.requirePodcast(podcastId);
        if (episodeRepo.existsByPodcastIdAndNumber(podcastId, req.number())) {
            throw ApiException.conflict("该集数已存在");
        }
        Episode e = new Episode();
        e.setPodcastId(podcastId);
        e.setNumber(req.number());
        e.setTitle(req.title());
        e.setTheme(req.theme());
        e.setRecordDate(req.recordDate());
        e.setStatus(req.status() != null ? req.status() : EpisodeStatus.PLANNING);
        e = episodeRepo.save(e);
        auditService.log("EPISODE_CREATE", "Episode", e.getId(), e.getTitle());
        return e;
    }

    @Transactional
    public Episode update(Long episodeId, UpdateEpisodeRequest req) {
        Episode e = accessGuard.requireEpisode(episodeId);
        if (req.number() != null && !req.number().equals(e.getNumber())) {
            if (episodeRepo.existsByPodcastIdAndNumber(e.getPodcastId(), req.number())) {
                throw ApiException.conflict("该集数已存在");
            }
            e.setNumber(req.number());
        }
        if (req.title() != null) e.setTitle(req.title());
        if (req.theme() != null) e.setTheme(req.theme());
        if (req.recordDate() != null) e.setRecordDate(req.recordDate());
        if (req.status() != null) e.setStatus(req.status());
        if (req.finalAudioUrl() != null) e.setFinalAudioUrl(req.finalAudioUrl());
        e = episodeRepo.save(e);
        auditService.log("EPISODE_UPDATE", "Episode", e.getId(), e.getTitle());
        return e;
    }

    @Transactional
    public void delete(Long episodeId) {
        Episode e = accessGuard.requireEpisode(episodeId);
        episodeRepo.delete(e);
        auditService.log("EPISODE_DELETE", "Episode", episodeId, e.getTitle());
    }
}
