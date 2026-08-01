package com.podcast.collab.repository;

import com.podcast.collab.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByEpisodeId(Long episodeId);
    List<Task> findByAssigneeId(Long assigneeId);
    long countByAssigneeIdAndStatusNot(Long assigneeId, String status);
    List<Task> findByDueDateBeforeAndStatusNot(java.time.LocalDate date, String status);
}
