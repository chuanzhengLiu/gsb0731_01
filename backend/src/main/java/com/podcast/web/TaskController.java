package com.podcast.web;

import com.podcast.domain.Task;
import com.podcast.service.TaskService;
import com.podcast.web.dto.TaskDtos.ChangeTaskStatusRequest;
import com.podcast.web.dto.TaskDtos.CreateTaskRequest;
import com.podcast.web.dto.TaskDtos.TaskResponse;
import com.podcast.web.dto.TaskDtos.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task board endpoints (README §4.1). Role rules are enforced in the service:
 * producers manage assignments; assignees progress their own task status.
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    private TaskResponse toResponse(Task t) {
        return TaskResponse.from(t, service.assigneeName(t.getAssigneeId()), service.isOverdue(t));
    }

    @GetMapping("/episodes/{episodeId}/tasks")
    public List<TaskResponse> list(@PathVariable Long episodeId) {
        return service.listByEpisode(episodeId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/episodes/{episodeId}/tasks")
    public TaskResponse create(@PathVariable Long episodeId,
                               @Valid @RequestBody CreateTaskRequest req) {
        return toResponse(service.create(episodeId, req));
    }

    @PutMapping("/tasks/{taskId}")
    public TaskResponse update(@PathVariable Long taskId,
                               @Valid @RequestBody UpdateTaskRequest req) {
        return toResponse(service.update(taskId, req));
    }

    @PatchMapping("/tasks/{taskId}/status")
    public TaskResponse changeStatus(@PathVariable Long taskId,
                                     @Valid @RequestBody ChangeTaskStatusRequest req) {
        return toResponse(service.changeStatus(taskId, req.status()));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId) {
        service.delete(taskId);
        return ResponseEntity.noContent().build();
    }
}
