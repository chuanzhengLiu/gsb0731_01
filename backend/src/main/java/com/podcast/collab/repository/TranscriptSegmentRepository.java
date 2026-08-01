package com.podcast.collab.repository;

import com.podcast.collab.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {
    List<TranscriptSegment> findByAudioVersionIdOrderByStartTimeMs(Long audioVersionId);
    void deleteByAudioVersionId(Long audioVersionId);
}
