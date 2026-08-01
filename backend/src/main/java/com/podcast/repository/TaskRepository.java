package com.podcast.repository;

import com.podcast.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByEpisodeId(Long episodeId);
    List<Task> findByEpisodeIdOrderByCreatedAtAsc(Long episodeId);
    List<Task> findByAssigneeId(Long assigneeId);

    /** Whether a user is assigned any task on an episode (README §9 剪辑师权限). */
    boolean existsByEpisodeIdAndAssigneeId(Long episodeId, Long assigneeId);
}
