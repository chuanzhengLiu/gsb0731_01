package com.podcast.collab.web;

import com.podcast.collab.dto.TaskDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/episodes/{episodeId}/tasks")
    public List<TaskResponse> byEpisode(@PathVariable Long episodeId) {
        return taskService.listByEpisode(episodeId, SecurityUtils.requireUser());
    }

    @PostMapping("/episodes/{episodeId}/tasks")
    public TaskResponse create(@PathVariable Long episodeId, @Valid @RequestBody CreateTaskRequest req) {
        return taskService.create(episodeId, req, SecurityUtils.requireUser());
    }

    @GetMapping("/tasks/mine")
    public List<TaskResponse> mine() {
        return taskService.listMine(SecurityUtils.requireUser());
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskResponse update(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.update(taskId, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/tasks/{taskId}")
    public void delete(@PathVariable Long taskId) {
        taskService.delete(taskId, SecurityUtils.requireUser());
    }
}
