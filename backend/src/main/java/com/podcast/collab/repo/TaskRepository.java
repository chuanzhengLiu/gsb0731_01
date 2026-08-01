package com.podcast.collab.repo;

import com.podcast.collab.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
    List<Task> findByAssigneeIdOrderByDueDateAsc(Long assigneeId);
}
