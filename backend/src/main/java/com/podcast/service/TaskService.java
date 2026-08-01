package com.podcast.service;

import com.podcast.domain.Episode;
import com.podcast.domain.Task;
import com.podcast.domain.TaskStatus;
import com.podcast.domain.User;
import com.podcast.repository.EpisodeRepository;
import com.podcast.repository.TaskRepository;
import com.podcast.repository.UserRepository;
import com.podcast.web.ApiException;
import com.podcast.web.dto.TaskDtos.CreateTaskRequest;
import com.podcast.web.dto.TaskDtos.UpdateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-episode task board (README §4.1 任务看板): producers assign tasks with a
 * due date and status; the assignee (剪辑师) progresses their own task. This is
 * the entry point that makes {@link AuthorizationService}'s assignment-based
 * rules (剪辑师只能操作分配给自己的单集) usable.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;
    private final EpisodeRepository episodeRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final AuditService audit;

    public TaskService(TaskRepository taskRepo, UserRepository userRepo,
                       EpisodeRepository episodeRepo, AccessGuard accessGuard,
                       AuthorizationService authz, AuditService audit) {
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.episodeRepo = episodeRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Task> listByEpisode(Long episodeId) {
        accessGuard.requireEpisode(episodeId); // team isolation
        return taskRepo.findByEpisodeIdOrderByCreatedAtAsc(episodeId);
    }

    /** Resolves an assignee's display name, guarding same-team membership. */
    @Transactional(readOnly = true)
    public String assigneeName(Long assigneeId) {
        if (assigneeId == null) return null;
        return userRepo.findById(assigneeId).map(User::getName).orElse(null);
    }

    public boolean isOverdue(Task t) {
        return t.getStatus() != TaskStatus.DONE
                && t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now());
    }

    private void validateAssignee(Long assigneeId) {
        if (assigneeId == null) return;
        Long teamId = accessGuard.requireTeamId();
        User u = userRepo.findById(assigneeId)
                .orElseThrow(() -> ApiException.badRequest("指派的成员不存在"));
        if (!teamId.equals(u.getTeamId())) {
            throw ApiException.badRequest("只能指派本团队成员");
        }
    }

    @Transactional
    public Task create(Long episodeId, CreateTaskRequest req) {
        Episode e = accessGuard.requireEpisode(episodeId);
        authz.checkCanManageTasks(); // README §9: producers assign tasks
        validateAssignee(req.assigneeId());
        Task t = new Task();
        t.setEpisodeId(e.getId());
        t.setDescription(req.description());
        t.setAssigneeId(req.assigneeId());
        t.setDueDate(req.dueDate());
        t.setStatus(TaskStatus.TODO);
        t = taskRepo.save(t);
        audit.log("TASK_CREATE", "Task", t.getId(),
                "episode=" + episodeId + " assignee=" + req.assigneeId());
        return t;
    }

    private Task requireTask(Long taskId) {
        Task t = taskRepo.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("任务不存在"));
        accessGuard.requireEpisode(t.getEpisodeId()); // team isolation
        return t;
    }

    /**
     * Full update: description/assignee/due-date changes require task-management
     * rights; a lone status change may also be done by the assignee.
     */
    @Transactional
    public Task update(Long taskId, UpdateTaskRequest req) {
        Task t = requireTask(taskId);

        boolean fieldsChanging = req.description() != null
                || req.assigneeId() != null || req.dueDate() != null;
        boolean statusChanging = req.status() != null && req.status() != t.getStatus();

        if (fieldsChanging) {
            authz.checkCanManageTasks();
            if (req.assigneeId() != null) {
                validateAssignee(req.assigneeId());
                t.setAssigneeId(req.assigneeId());
            }
            if (req.description() != null) t.setDescription(req.description());
            if (req.dueDate() != null) t.setDueDate(req.dueDate());
        }
        if (statusChanging) {
            authz.checkCanUpdateTaskStatus(t.getAssigneeId());
            t.setStatus(req.status());
        }
        t = taskRepo.save(t);
        audit.log("TASK_UPDATE", "Task", t.getId(), "status=" + t.getStatus());
        return t;
    }

    /** Dedicated status endpoint used by an assignee to progress their task. */
    @Transactional
    public Task changeStatus(Long taskId, TaskStatus status) {
        Task t = requireTask(taskId);
        authz.checkCanUpdateTaskStatus(t.getAssigneeId());
        TaskStatus from = t.getStatus();
        t.setStatus(status);
        t = taskRepo.save(t);
        audit.log("TASK_STATUS_CHANGE", "Task", t.getId(), from + " -> " + status);
        return t;
    }

    @Transactional
    public void delete(Long taskId) {
        Task t = requireTask(taskId);
        authz.checkCanManageTasks();
        taskRepo.delete(t);
        audit.log("TASK_DELETE", "Task", taskId, null);
    }
}
