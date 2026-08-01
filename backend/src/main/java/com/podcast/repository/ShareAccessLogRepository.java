package com.podcast.repository;

import com.podcast.domain.ShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, Long> {
    List<ShareAccessLog> findByShareLinkIdOrderByAccessedAtDesc(Long shareLinkId);
    long countByShareLinkId(Long shareLinkId);
}
