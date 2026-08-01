package com.podcast.collab.dto.task;

import com.podcast.collab.entity.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateTaskRequest {

    private String title;

    private String description;

    private Long assigneeId;

    private LocalDateTime dueDate;

    private TaskStatus status;
}
