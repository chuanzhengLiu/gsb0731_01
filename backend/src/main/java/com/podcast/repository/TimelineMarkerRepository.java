package com.podcast.repository;

import com.podcast.domain.MarkerStatus;
import com.podcast.domain.MarkerType;
import com.podcast.domain.TimelineMarker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TimelineMarkerRepository extends JpaRepository<TimelineMarker, Long> {

    List<TimelineMarker> findByAudioVersionIdOrderByStartTimeMsAsc(Long audioVersionId);

    List<TimelineMarker> findByAudioVersionIdIn(List<Long> audioVersionIds);

    /**
     * Filter + keyword search (README §4.2). NULL params are ignored.
     */
    @Query("""
        SELECT m FROM TimelineMarker m
        WHERE m.audioVersionId = :audioVersionId
          AND (:type IS NULL OR m.type = :type)
          AND (:status IS NULL OR m.status = :status)
          AND (:createdBy IS NULL OR m.createdBy = :createdBy)
          AND (:keyword IS NULL OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY m.startTimeMs ASC
        """)
    List<TimelineMarker> search(@Param("audioVersionId") Long audioVersionId,
                                @Param("type") MarkerType type,
                                @Param("status") MarkerStatus status,
                                @Param("createdBy") Long createdBy,
                                @Param("keyword") String keyword);
}
