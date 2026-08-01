package com.podcast.collab.dto.task;

import com.podcast.collab.entity.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private Long episodeId;
    private Long assigneeId;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private TaskStatus status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String assigneeName;
    private String creatorName;
}
