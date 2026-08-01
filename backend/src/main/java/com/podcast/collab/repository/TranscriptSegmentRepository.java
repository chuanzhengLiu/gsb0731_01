package com.podcast.collab.repository;

import com.podcast.collab.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {
    List<TranscriptSegment> findByAudioVersionIdOrderBySegmentOrderAsc(Long audioVersionId);
    void deleteByAudioVersionId(Long audioVersionId);
}
