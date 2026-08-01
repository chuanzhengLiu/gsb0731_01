package com.podcast.service;

import com.podcast.domain.Episode;
import com.podcast.domain.Podcast;
import com.podcast.repository.EpisodeRepository;
import com.podcast.repository.PodcastRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Service;

/**
 * Centralizes team-isolation checks (README §8): every entity access is
 * validated against the caller's team_id before being returned.
 */
@Service
public class AccessGuard {

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;

    public AccessGuard(PodcastRepository podcastRepo, EpisodeRepository episodeRepo) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
    }

    public Long requireTeamId() {
        Long teamId = SecurityUtils.currentTeamId();
        if (teamId == null) {
            throw ApiException.forbidden("当前用户未加入任何团队");
        }
        return teamId;
    }

    /** Loads a podcast ensuring it belongs to the caller's team. */
    public Podcast requirePodcast(Long podcastId) {
        Long teamId = requireTeamId();
        return podcastRepo.findByIdAndTeamId(podcastId, teamId)
                .orElseThrow(() -> ApiException.notFound("节目不存在或无权访问"));
    }

    /** Loads an episode ensuring its podcast belongs to the caller's team. */
    public Episode requireEpisode(Long episodeId) {
        Episode episode = episodeRepo.findById(episodeId)
                .orElseThrow(() -> ApiException.notFound("单集不存在"));
        // Validate podcast ownership (throws if cross-team).
        requirePodcast(episode.getPodcastId());
        return episode;
    }
}
