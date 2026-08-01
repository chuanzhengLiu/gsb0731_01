package com.podcast.collab.repository;

import com.podcast.collab.entity.Task;
import com.podcast.collab.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
    List<Task> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);
    List<Task> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);
    List<Task> findByEpisodeIdAndStatus(Long episodeId, TaskStatus status);
    long countByEpisodeId(Long episodeId);
    long countByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);
    List<Task> findByEpisodeIdIn(Collection<Long> episodeIds);
}
