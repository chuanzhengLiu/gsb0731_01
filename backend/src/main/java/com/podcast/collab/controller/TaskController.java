package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.Task;
import com.podcast.collab.repository.TaskRepository;
import com.podcast.collab.security.ForbiddenException;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 任务看板：每集任务列表（负责人、截止日期、状态） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {
    private final TaskRepository taskRepository;
    private final TeamGuard teamGuard;

    @GetMapping("/episodes/{episodeId}/tasks")
    public List<Task> list(@PathVariable Long episodeId) {
        teamGuard.requireEpisode(episodeId);
        return taskRepository.findByEpisodeId(episodeId);
    }

    /** 分配给我的任务（剪辑师视角） */
    @GetMapping("/tasks/mine")
    public List<Task> mine() {
        return taskRepository.findByAssigneeId(SecurityUtils.currentUserId());
    }

    @PostMapping("/episodes/{episodeId}/tasks")
    public Task create(@PathVariable Long episodeId, @Valid @RequestBody TaskRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        teamGuard.requireEpisode(episodeId);
        Task task = new Task();
        task.setEpisodeId(episodeId);
        task.setDescription(req.description());
        task.setAssigneeId(req.assigneeId());
        task.setDueDate(req.dueDate());
        task.setCreatedBy(SecurityUtils.currentUserId());
        return taskRepository.save(task);
    }

    @PutMapping("/tasks/{id}")
    public Task update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        Task task = requireTask(id);
        String role = SecurityUtils.currentUser().getRole();
        // 制作人可改一切，剪辑师只能操作分配给自己的
        if ("EDITOR".equals(role) && !SecurityUtils.currentUserId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("只能操作分配给自己的任务");
        }
        task.setDescription(req.description());
        task.setAssigneeId(req.assigneeId());
        task.setDueDate(req.dueDate());
        return taskRepository.save(task);
    }

    @PutMapping("/tasks/{id}/status")
    public Task updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest req) {
        Task task = requireTask(id);
        String role = SecurityUtils.currentUser().getRole();
        if ("EDITOR".equals(role) && !SecurityUtils.currentUserId().equals(task.getAssigneeId())) {
            throw new ForbiddenException("只能操作分配给自己的任务");
        }
        task.setStatus(req.status());
        return taskRepository.save(task);
    }

    @DeleteMapping("/tasks/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Task task = requireTask(id);
        taskRepository.delete(task);
        return Map.of("message", "任务已删除");
    }

    private Task requireTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        teamGuard.requireEpisode(task.getEpisodeId());
        return task;
    }
}
