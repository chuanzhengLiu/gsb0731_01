package com.podcast.collab.service;

import com.podcast.collab.dto.task.CreateTaskRequest;
import com.podcast.collab.dto.task.TaskResponse;
import com.podcast.collab.dto.task.UpdateTaskRequest;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.Task;
import com.podcast.collab.entity.TeamMember;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.TaskStatus;
import com.podcast.collab.entity.enums.TeamRole;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TaskRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaskService(TaskRepository taskRepository,
                       EpisodeRepository episodeRepository,
                       PodcastRepository podcastRepository,
                       TeamMemberRepository teamMemberRepository,
                       UserRepository userRepository,
                       AuditService auditService) {
        this.taskRepository = taskRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaskResponse create(Long episodeId, CreateTaskRequest request, Long userId) {
        verifyEpisodeTeamAccess(episodeId, userId);

        Task task = Task.builder()
                .episodeId(episodeId)
                .title(request.getTitle())
                .description(request.getDescription())
                .assigneeId(request.getAssigneeId())
                .dueDate(request.getDueDate())
                .status(TaskStatus.TODO)
                .createdBy(userId)
                .build();
        task = taskRepository.save(task);

        auditService.log(userId, "CREATE_TASK", "Task", task.getId(),
                "Task created: " + task.getTitle());

        return mapToResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long id, Long userId) {
        Task task = findTaskOrThrow(id);
        verifyEpisodeTeamAccess(task.getEpisodeId(), userId);
        return mapToResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listByEpisode(Long episodeId, Long userId) {
        verifyEpisodeTeamAccess(episodeId, userId);
        return taskRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status, Long userId) {
        Task task = findTaskOrThrow(id);
        verifyTaskWriteAccess(task, userId);

        task.setStatus(status);
        task = taskRepository.save(task);

        auditService.log(userId, "UPDATE_TASK_STATUS", "Task", task.getId(),
                "Task status changed to " + status);

        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request, Long userId) {
        Task task = findTaskOrThrow(id);
        verifyTaskWriteAccess(task, userId);

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getAssigneeId() != null) {
            task.setAssigneeId(request.getAssigneeId());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        task = taskRepository.save(task);

        auditService.log(userId, "UPDATE_TASK", "Task", task.getId(),
                "Task updated: " + task.getTitle());

        return mapToResponse(task);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Task task = findTaskOrThrow(id);
        verifyEpisodeTeamAccess(task.getEpisodeId(), userId);
        taskRepository.delete(task);

        auditService.log(userId, "DELETE_TASK", "Task", id,
                "Task deleted: " + task.getTitle());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listMyTasks(Long userId) {
        return taskRepository.findByAssigneeIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private Episode findEpisodeOrThrow(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyEpisodeTeamAccess(Long episodeId, Long userId) {
        Episode episode = findEpisodeOrThrow(episodeId);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        if (!teamMemberRepository.existsByTeamIdAndUserId(podcast.getTeamId(), userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private void verifyTaskWriteAccess(Task task, Long userId) {
        Episode episode = findEpisodeOrThrow(task.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());

        TeamMember membership = teamMemberRepository
                .findByTeamIdAndUserId(podcast.getTeamId(), userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

        if (membership.getRoleInTeam() == TeamRole.EDITOR
                && !userId.equals(task.getAssigneeId())) {
            throw new AccessDeniedException("Editors can only update tasks assigned to them");
        }
    }

    private TaskResponse mapToResponse(Task task) {
        String assigneeName = null;
        if (task.getAssigneeId() != null) {
            assigneeName = userRepository.findById(task.getAssigneeId())
                    .map(User::getName)
                    .orElse(null);
        }

        String creatorName = userRepository.findById(task.getCreatedBy())
                .map(User::getName)
                .orElse(null);

        return TaskResponse.builder()
                .id(task.getId())
                .episodeId(task.getEpisodeId())
                .assigneeId(task.getAssigneeId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .status(task.getStatus())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .assigneeName(assigneeName)
                .creatorName(creatorName)
                .build();
    }
}
