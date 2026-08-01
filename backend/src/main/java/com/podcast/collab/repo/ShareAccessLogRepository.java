package com.podcast.collab.repo;

import com.podcast.collab.domain.ShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, Long> {
}
