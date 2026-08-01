package com.podcast.collab.service;

import com.podcast.collab.dto.episode.CreateEpisodeRequest;
import com.podcast.collab.dto.episode.EpisodeResponse;
import com.podcast.collab.dto.episode.UpdateEpisodeRequest;
import com.podcast.collab.dto.episode.UpdateEpisodeStatusRequest;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.EpisodeStatus;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TaskRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TimelineMarkerRepository;
import com.podcast.collab.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TaskRepository taskRepository;
    private final TimelineMarkerRepository timelineMarkerRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public EpisodeService(EpisodeRepository episodeRepository,
                          PodcastRepository podcastRepository,
                          TaskRepository taskRepository,
                          TimelineMarkerRepository timelineMarkerRepository,
                          TeamMemberRepository teamMemberRepository,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.taskRepository = taskRepository;
        this.timelineMarkerRepository = timelineMarkerRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public EpisodeResponse create(Long podcastId, CreateEpisodeRequest request, Long userId) {
        Podcast podcast = findPodcastOrThrow(podcastId);
        verifyTeamMembership(podcast.getTeamId(), userId);

        int number;
        if (request.getNumber() != null) {
            number = request.getNumber();
        } else {
            number = episodeRepository.findByPodcastIdOrderByNumberDesc(podcastId).stream()
                    .findFirst()
                    .map(e -> e.getNumber() + 1)
                    .orElse(1);
        }

        Episode episode = Episode.builder()
                .podcastId(podcastId)
                .number(number)
                .title(request.getTitle())
                .theme(request.getTheme())
                .recordDate(request.getRecordDate())
                .status(EpisodeStatus.PLANNING)
                .scheduledAt(request.getScheduledAt())
                .createdBy(userId)
                .build();
        episode = episodeRepository.save(episode);

        auditService.log(userId, "CREATE_EPISODE", "Episode", episode.getId(),
                "Episode created: " + episode.getTitle());

        return mapToResponse(episode, podcast);
    }

    @Transactional(readOnly = true)
    public EpisodeResponse getById(Long id, Long userId) {
        Episode episode = findEpisodeOrThrow(id);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);
        return mapToResponse(episode, podcast);
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listByPodcast(Long podcastId, Long userId) {
        Podcast podcast = findPodcastOrThrow(podcastId);
        verifyTeamMembership(podcast.getTeamId(), userId);
        return episodeRepository.findByPodcastIdOrderByNumberDesc(podcastId).stream()
                .map(episode -> mapToResponse(episode, podcast))
                .toList();
    }

    @Transactional
    public EpisodeResponse updateStatus(Long id, UpdateEpisodeStatusRequest request, Long userId) {
        Episode episode = findEpisodeOrThrow(id);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        validateStatusTransition(episode.getStatus(), request.getStatus());
        episode.setStatus(request.getStatus());
        episode = episodeRepository.save(episode);

        auditService.log(userId, "UPDATE_EPISODE_STATUS", "Episode", episode.getId(),
                "Episode status changed to " + request.getStatus());

        return mapToResponse(episode, podcast);
    }

    @Transactional
    public EpisodeResponse update(Long id, UpdateEpisodeRequest request, Long userId) {
        Episode episode = findEpisodeOrThrow(id);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        if (request.getTitle() != null) {
            episode.setTitle(request.getTitle());
        }
        if (request.getTheme() != null) {
            episode.setTheme(request.getTheme());
        }
        if (request.getRecordDate() != null) {
            episode.setRecordDate(request.getRecordDate());
        }
        if (request.getScheduledAt() != null) {
            episode.setScheduledAt(request.getScheduledAt());
        }
        episode = episodeRepository.save(episode);

        auditService.log(userId, "UPDATE_EPISODE", "Episode", episode.getId(),
                "Episode updated: " + episode.getTitle());

        return mapToResponse(episode, podcast);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Episode episode = findEpisodeOrThrow(id);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);
        episodeRepository.delete(episode);

        auditService.log(userId, "DELETE_EPISODE", "Episode", id,
                "Episode deleted: " + episode.getTitle());
    }

    private void validateStatusTransition(EpisodeStatus current, EpisodeStatus target) {
        if (current == target) {
            return;
        }
        int currentOrdinal = current.ordinal();
        int targetOrdinal = target.ordinal();

        if (targetOrdinal > currentOrdinal + 1) {
            throw new BadRequestException(
                    "Cannot skip statuses. Forward transition from " + current + " to " + target + " is not allowed");
        }
    }

    private Episode findEpisodeOrThrow(Long id) {
        return episodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
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

    private EpisodeResponse mapToResponse(Episode episode, Podcast podcast) {
        String createdByName = userRepository.findById(episode.getCreatedBy())
                .map(User::getName)
                .orElse(null);

        long markerCount = timelineMarkerRepository.countByEpisodeId(episode.getId());
        long taskCount = taskRepository.countByEpisodeId(episode.getId());

        return EpisodeResponse.builder()
                .id(episode.getId())
                .podcastId(episode.getPodcastId())
                .number(episode.getNumber())
                .title(episode.getTitle())
                .theme(episode.getTheme())
                .recordDate(episode.getRecordDate())
                .status(episode.getStatus())
                .finalAudioUrl(episode.getFinalAudioUrl())
                .scheduledAt(episode.getScheduledAt())
                .createdBy(episode.getCreatedBy())
                .createdAt(episode.getCreatedAt())
                .updatedAt(episode.getUpdatedAt())
                .podcastName(podcast.getName())
                .createdByName(createdByName)
                .markerCount(markerCount)
                .taskCount(taskCount)
                .build();
    }
}
