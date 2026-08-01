package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TaskDtos() {

    public record TaskResponse(Long id, Long episodeId, Long assigneeId, String assigneeName,
                               String title, String description, Instant dueDate,
                               Enums.TaskStatus status, Instant createdAt) {}

    public record CreateTaskRequest(
            Long assigneeId,
            @NotBlank String title,
            String description,
            Instant dueDate
    ) {}

    public record UpdateTaskRequest(
            Long assigneeId,
            String title,
            String description,
            Instant dueDate,
            Enums.TaskStatus status
    ) {}
}
