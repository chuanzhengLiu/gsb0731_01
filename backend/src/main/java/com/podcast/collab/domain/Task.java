package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task extends BaseEntity {
    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "due_date")
    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.TaskStatus status = Enums.TaskStatus.TODO;
}
