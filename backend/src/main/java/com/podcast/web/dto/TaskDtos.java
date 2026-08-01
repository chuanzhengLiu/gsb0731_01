package com.podcast.web.dto;

import com.podcast.domain.Task;
import com.podcast.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class TaskDtos {

    public record CreateTaskRequest(
            @NotBlank String description,
            Long assigneeId,
            LocalDate dueDate
    ) {}

    public record UpdateTaskRequest(
            String description,
            Long assigneeId,
            LocalDate dueDate,
            TaskStatus status
    ) {}

    /** Dedicated status update (assignee may flip their own task's status). */
    public record ChangeTaskStatusRequest(
            @NotNull TaskStatus status
    ) {}

    public record TaskResponse(
            Long id,
            Long episodeId,
            Long assigneeId,
            String assigneeName,
            String description,
            LocalDate dueDate,
            String status,
            boolean overdue,
            String createdAt
    ) {
        public static TaskResponse from(Task t, String assigneeName, boolean overdue) {
            return new TaskResponse(
                    t.getId(), t.getEpisodeId(), t.getAssigneeId(), assigneeName,
                    t.getDescription(), t.getDueDate(), t.getStatus().name(),
                    overdue, t.getCreatedAt().toString());
        }
    }
}
