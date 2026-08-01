package com.podcast.collab.service;

import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.security.ForbiddenException;
import com.podcast.collab.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 团队隔离守卫：所有资源访问必须校验归属当前团队 */
@Service
@RequiredArgsConstructor
public class TeamGuard {
    private final PodcastRepository podcastRepository;
    private final EpisodeRepository episodeRepository;

    public Podcast requirePodcast(Long podcastId) {
        Long teamId = SecurityUtils.currentTeamId();
        Podcast podcast = podcastRepository.findById(podcastId)
                .orElseThrow(() -> new IllegalArgumentException("节目不存在"));
        if (!podcast.getTeamId().equals(teamId)) {
            throw new ForbiddenException("无权访问该节目");
        }
        return podcast;
    }

    public Episode requireEpisode(Long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new IllegalArgumentException("单集不存在"));
        requirePodcast(episode.getPodcastId());
        return episode;
    }
}
