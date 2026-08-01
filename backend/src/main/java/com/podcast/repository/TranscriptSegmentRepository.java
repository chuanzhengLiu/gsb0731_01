package com.podcast.repository;

import com.podcast.domain.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {
    List<TranscriptSegment> findByAudioVersionIdOrderBySegmentIndexAsc(Long audioVersionId);
    long countByAudioVersionId(Long audioVersionId);
    void deleteByAudioVersionId(Long audioVersionId);
}
