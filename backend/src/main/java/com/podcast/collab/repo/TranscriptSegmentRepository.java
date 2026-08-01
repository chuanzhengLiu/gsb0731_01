package com.podcast.collab.repo;

import com.podcast.collab.domain.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {
    List<TranscriptSegment> findByAudioVersionIdOrderBySeqAsc(Long audioVersionId);
    void deleteByAudioVersionId(Long audioVersionId);
}
