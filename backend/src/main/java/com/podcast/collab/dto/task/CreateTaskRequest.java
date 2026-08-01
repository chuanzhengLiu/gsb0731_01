package com.podcast.collab.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;

    private Long assigneeId;

    private LocalDateTime dueDate;
}
