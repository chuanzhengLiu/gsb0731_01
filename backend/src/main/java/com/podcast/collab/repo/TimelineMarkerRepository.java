package com.podcast.collab.repo;

import com.podcast.collab.domain.Enums;
import com.podcast.collab.domain.TimelineMarker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimelineMarkerRepository extends JpaRepository<TimelineMarker, Long> {
    List<TimelineMarker> findByAudioVersionIdOrderByStartTimeMsAsc(Long audioVersionId);

    @Query("select m from TimelineMarker m where m.audioVersionId = :vid " +
            "and (:type is null or m.type = :type) " +
            "and (:status is null or m.status = :status) " +
            "and (:createdBy is null or m.createdBy = :createdBy) " +
            "and (:keyword is null or lower(m.description) like lower(concat('%', :keyword, '%'))) " +
            "order by m.startTimeMs asc")
    List<TimelineMarker> search(@Param("vid") Long versionId,
                                @Param("type") Enums.MarkerType type,
                                @Param("status") Enums.MarkerStatus status,
                                @Param("createdBy") Long createdBy,
                                @Param("keyword") String keyword);

    long countByAudioVersionIdAndStatus(Long audioVersionId, Enums.MarkerStatus status);
}
