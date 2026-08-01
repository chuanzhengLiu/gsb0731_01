package com.podcast.service;

import com.podcast.domain.Podcast;
import com.podcast.repository.PodcastRepository;
import com.podcast.web.dto.PodcastDtos.CreatePodcastRequest;
import com.podcast.web.dto.PodcastDtos.UpdatePodcastRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PodcastService {

    private final PodcastRepository podcastRepo;
    private final AccessGuard accessGuard;
    private final AuditService auditService;

    public PodcastService(PodcastRepository podcastRepo, AccessGuard accessGuard, AuditService auditService) {
        this.podcastRepo = podcastRepo;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Podcast> list() {
        return podcastRepo.findByTeamId(accessGuard.requireTeamId());
    }

    @Transactional(readOnly = true)
    public Podcast get(Long id) {
        return accessGuard.requirePodcast(id);
    }

    @Transactional
    public Podcast create(CreatePodcastRequest req) {
        Long teamId = accessGuard.requireTeamId();
        Podcast p = new Podcast();
        p.setTeamId(teamId);
        p.setName(req.name());
        p.setType(req.type());
        p.setUpdateFrequency(req.updateFrequency());
        p.setTargetDurationMs(req.targetDurationMs());
        p.setStructureTemplateJson(req.structureTemplateJson());
        p = podcastRepo.save(p);
        auditService.log("PODCAST_CREATE", "Podcast", p.getId(), p.getName());
        return p;
    }

    @Transactional
    public Podcast update(Long id, UpdatePodcastRequest req) {
        Podcast p = accessGuard.requirePodcast(id);
        if (req.name() != null) p.setName(req.name());
        if (req.type() != null) p.setType(req.type());
        if (req.updateFrequency() != null) p.setUpdateFrequency(req.updateFrequency());
        if (req.targetDurationMs() != null) p.setTargetDurationMs(req.targetDurationMs());
        if (req.structureTemplateJson() != null) p.setStructureTemplateJson(req.structureTemplateJson());
        p = podcastRepo.save(p);
        auditService.log("PODCAST_UPDATE", "Podcast", p.getId(), p.getName());
        return p;
    }

    @Transactional
    public void delete(Long id) {
        Podcast p = accessGuard.requirePodcast(id);
        podcastRepo.delete(p);
        auditService.log("PODCAST_DELETE", "Podcast", id, p.getName());
    }
}
