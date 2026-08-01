package com.podcast.collab.service;

import com.podcast.collab.dto.podcast.CreatePodcastRequest;
import com.podcast.collab.dto.podcast.PodcastResponse;
import com.podcast.collab.dto.podcast.UpdatePodcastRequest;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PodcastService {

    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;

    public PodcastService(PodcastRepository podcastRepository,
                          TeamMemberRepository teamMemberRepository,
                          AuditService auditService) {
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PodcastResponse create(Long teamId, CreatePodcastRequest request, Long userId) {
        verifyTeamMembership(teamId, userId);

        Podcast podcast = Podcast.builder()
                .teamId(teamId)
                .name(request.getName())
                .type(request.getType())
                .updateFrequency(request.getUpdateFrequency())
                .targetDuration(request.getTargetDuration())
                .structureTemplateJson(request.getStructureTemplateJson())
                .description(request.getDescription())
                .build();
        podcast = podcastRepository.save(podcast);

        auditService.log(userId, "CREATE_PODCAST", "Podcast", podcast.getId(),
                "Podcast created: " + podcast.getName());

        return mapToResponse(podcast);
    }

    @Transactional(readOnly = true)
    public PodcastResponse getById(Long id, Long userId) {
        Podcast podcast = findPodcastOrThrow(id);
        verifyTeamMembership(podcast.getTeamId(), userId);
        return mapToResponse(podcast);
    }

    @Transactional(readOnly = true)
    public List<PodcastResponse> listByTeam(Long teamId, Long userId) {
        verifyTeamMembership(teamId, userId);
        return podcastRepository.findByTeamId(teamId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public PodcastResponse update(Long id, UpdatePodcastRequest request, Long userId) {
        Podcast podcast = findPodcastOrThrow(id);
        verifyTeamMembership(podcast.getTeamId(), userId);

        if (request.getName() != null) {
            podcast.setName(request.getName());
        }
        if (request.getType() != null) {
            podcast.setType(request.getType());
        }
        if (request.getUpdateFrequency() != null) {
            podcast.setUpdateFrequency(request.getUpdateFrequency());
        }
        if (request.getTargetDuration() != null) {
            podcast.setTargetDuration(request.getTargetDuration());
        }
        if (request.getStructureTemplateJson() != null) {
            podcast.setStructureTemplateJson(request.getStructureTemplateJson());
        }
        if (request.getDescription() != null) {
            podcast.setDescription(request.getDescription());
        }
        podcast = podcastRepository.save(podcast);

        auditService.log(userId, "UPDATE_PODCAST", "Podcast", podcast.getId(),
                "Podcast updated: " + podcast.getName());

        return mapToResponse(podcast);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Podcast podcast = findPodcastOrThrow(id);
        verifyTeamMembership(podcast.getTeamId(), userId);
        podcastRepository.delete(podcast);

        auditService.log(userId, "DELETE_PODCAST", "Podcast", id,
                "Podcast deleted: " + podcast.getName());
    }

    private Podcast findPodcastOrThrow(Long id) {
        return podcastRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyTeamMembership(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private PodcastResponse mapToResponse(Podcast podcast) {
        return PodcastResponse.builder()
                .id(podcast.getId())
                .teamId(podcast.getTeamId())
                .name(podcast.getName())
                .type(podcast.getType())
                .updateFrequency(podcast.getUpdateFrequency())
                .targetDuration(podcast.getTargetDuration())
                .structureTemplateJson(podcast.getStructureTemplateJson())
                .description(podcast.getDescription())
                .coverImageUrl(podcast.getCoverImageUrl())
                .createdAt(podcast.getCreatedAt())
                .updatedAt(podcast.getUpdatedAt())
                .build();
    }
}
