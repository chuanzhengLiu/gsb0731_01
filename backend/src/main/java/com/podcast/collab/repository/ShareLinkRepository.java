package com.podcast.collab.repository;

import com.podcast.collab.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByToken(String token);
}
