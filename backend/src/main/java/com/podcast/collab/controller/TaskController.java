package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.task.CreateTaskRequest;
import com.podcast.collab.dto.task.TaskResponse;
import com.podcast.collab.dto.task.UpdateTaskRequest;
import com.podcast.collab.entity.enums.TaskStatus;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ApiResponse<TaskResponse> createTask(@RequestParam Long episodeId,
                                                 @Valid @RequestBody CreateTaskRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TaskResponse response = taskService.create(episodeId, request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> listByEpisode(@RequestParam Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TaskResponse> tasks = taskService.listByEpisode(episodeId, userId);
        return ApiResponse.ok(tasks);
    }

    @GetMapping("/my")
    public ApiResponse<List<TaskResponse>> listMyTasks() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TaskResponse> tasks = taskService.listMyTasks(userId);
        return ApiResponse.ok(tasks);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        TaskResponse response = taskService.getById(id, userId);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TaskResponse> updateStatus(@PathVariable Long id,
                                                   @RequestParam TaskStatus status) {
        Long userId = SecurityUtils.getCurrentUserId();
        TaskResponse response = taskService.updateStatus(id, status, userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateTaskRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TaskResponse response = taskService.update(id, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        taskService.delete(id, userId);
        return ApiResponse.ok("Task deleted successfully", null);
    }
}
