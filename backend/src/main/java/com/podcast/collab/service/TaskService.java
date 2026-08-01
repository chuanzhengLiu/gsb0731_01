package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.Task;
import com.podcast.collab.domain.User;
import com.podcast.collab.dto.TaskDtos.*;
import com.podcast.collab.repo.TaskRepository;
import com.podcast.collab.repo.UserRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    public TaskService(TaskRepository taskRepo, UserRepository userRepo, TeamGuard teamGuard,
                       AuditService auditService) {
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listByEpisode(Long episodeId, CurrentUser user) {
        teamGuard.requireEpisode(episodeId, user);
        List<Task> tasks = taskRepo.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listMine(CurrentUser user) {
        return taskRepo.findByAssigneeIdOrderByDueDateAsc(user.id()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse create(Long episodeId, CreateTaskRequest req, CurrentUser user) {
        teamGuard.requireEpisode(episodeId, user);
        if (!user.isProducerOrAbove()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Producer or admin can create tasks");
        }
        Task t = new Task();
        t.setEpisodeId(episodeId);
        t.setAssigneeId(req.assigneeId());
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setStatus(com.podcast.collab.domain.Enums.TaskStatus.TODO);
        taskRepo.save(t);
        auditService.log(user, "TASK_CREATE", "Task", t.getId(),
                Map.of("episodeId", episodeId, "assigneeId", req.assigneeId()));
        return toResponse(t);
    }

    @Transactional
    public TaskResponse update(Long taskId, UpdateTaskRequest req, CurrentUser user) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(t.getEpisodeId(), user);

        boolean isProducer = user.isProducerOrAbove();
        boolean isAssignee = t.getAssigneeId() != null && t.getAssigneeId().equals(user.id());
        if (!isProducer && !isAssignee) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only update tasks assigned to you");
        }
        if (isProducer) {
            if (req.assigneeId() != null) t.setAssigneeId(req.assigneeId());
            if (req.title() != null) t.setTitle(req.title());
            if (req.description() != null) t.setDescription(req.description());
            if (req.dueDate() != null) t.setDueDate(req.dueDate());
        }
        if (req.status() != null) t.setStatus(req.status());
        taskRepo.save(t);
        auditService.log(user, "TASK_UPDATE", "Task", t.getId(), Map.of("status", t.getStatus().name()));
        return toResponse(t);
    }

    @Transactional
    public void delete(Long taskId, CurrentUser user) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(t.getEpisodeId(), user);
        if (!user.isProducerOrAbove()) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        taskRepo.delete(t);
        auditService.log(user, "TASK_DELETE", "Task", taskId, null);
    }

    private TaskResponse toResponse(Task t) {
        String assigneeName = t.getAssigneeId() != null
                ? userRepo.findById(t.getAssigneeId()).map(User::getName).orElse(null) : null;
        return new TaskResponse(t.getId(), t.getEpisodeId(), t.getAssigneeId(), assigneeName,
                t.getTitle(), t.getDescription(), t.getDueDate(), t.getStatus(), t.getCreatedAt());
    }
}
