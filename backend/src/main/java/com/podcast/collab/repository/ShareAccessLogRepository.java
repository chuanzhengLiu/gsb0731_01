package com.podcast.collab.repository;

import com.podcast.collab.entity.ShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, Long> {
    List<ShareAccessLog> findByShareLinkIdOrderByAccessedAtDesc(Long shareLinkId);
}
