package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.Episode;
import com.podcast.collab.domain.Podcast;
import com.podcast.collab.repo.EpisodeRepository;
import com.podcast.collab.repo.PodcastRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Component;

@Component
public class TeamGuard {

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;

    public TeamGuard(PodcastRepository podcastRepo, EpisodeRepository episodeRepo) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
    }

    public Podcast requirePodcast(Long podcastId, CurrentUser user) {
        Podcast p = podcastRepo.findById(podcastId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Podcast not found"));
        if (!p.getTeamId().equals(user.teamId())) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        return p;
    }

    public Episode requireEpisode(Long episodeId, CurrentUser user) {
        Episode e = episodeRepo.findById(episodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Episode not found"));
        Podcast p = requirePodcast(e.getPodcastId(), user);
        return e;
    }
}
