package com.podcast.collab.repository;

import com.podcast.collab.entity.TimelineMarker;
import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TimelineMarkerRepository extends JpaRepository<TimelineMarker, Long> {
    List<TimelineMarker> findByAudioVersionIdOrderByStartTimeMsAsc(Long audioVersionId);

    @Query("SELECT m FROM TimelineMarker m WHERE m.audioVersionId = :versionId " +
           "AND (:type IS NULL OR m.type = :type) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:createdBy IS NULL OR m.createdBy = :createdBy) " +
           "AND (:keyword IS NULL OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY m.startTimeMs ASC")
    List<TimelineMarker> findFiltered(
            @Param("versionId") Long versionId,
            @Param("type") MarkerType type,
            @Param("status") MarkerStatus status,
            @Param("createdBy") Long createdBy,
            @Param("keyword") String keyword);

    long countByAudioVersionIdAndStatus(Long audioVersionId, MarkerStatus status);

    @Query("SELECT COUNT(m) FROM TimelineMarker m WHERE m.audioVersionId IN " +
           "(SELECT a.id FROM AudioVersion a WHERE a.episodeId = :episodeId)")
    long countByEpisodeId(@Param("episodeId") Long episodeId);

    @Query("SELECT m FROM TimelineMarker m WHERE m.audioVersionId IN " +
           "(SELECT a.id FROM AudioVersion a WHERE a.episodeId = :episodeId) " +
           "ORDER BY m.startTimeMs ASC")
    List<TimelineMarker> findByEpisodeId(@Param("episodeId") Long episodeId);

    @Query("SELECT COUNT(m) FROM TimelineMarker m WHERE m.audioVersionId IN " +
           "(SELECT a.id FROM AudioVersion a WHERE a.episodeId = :episodeId) " +
           "AND m.status = :status")
    long countByEpisodeIdAndStatus(@Param("episodeId") Long episodeId,
                                   @Param("status") MarkerStatus status);

    @Query("SELECT m FROM TimelineMarker m WHERE m.audioVersionId IN " +
           "(SELECT a.id FROM AudioVersion a WHERE a.episodeId IN :episodeIds)")
    List<TimelineMarker> findByEpisodeIdIn(@Param("episodeIds") java.util.Collection<Long> episodeIds);
}
