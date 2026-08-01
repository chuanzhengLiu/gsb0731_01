package com.podcast.collab.repository;

import com.podcast.collab.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
