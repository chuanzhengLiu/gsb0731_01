package com.podcast.collab.repository;

import com.podcast.collab.entity.MarkerStatus;
import com.podcast.collab.entity.MarkerType;
import com.podcast.collab.entity.TimelineMarker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimelineMarkerRepository extends JpaRepository<TimelineMarker, Long> {
    List<TimelineMarker> findByEpisodeIdOrderByStartTimeMs(Long episodeId);
    List<TimelineMarker> findByAudioVersionIdOrderByStartTimeMs(Long audioVersionId);
    List<TimelineMarker> findByEpisodeIdAndType(Long episodeId, MarkerType type);
    List<TimelineMarker> findByEpisodeIdAndStatus(Long episodeId, MarkerStatus status);
    List<TimelineMarker> findByEpisodeIdAndCreatedBy(Long episodeId, Long createdBy);
    long countByEpisodeId(Long episodeId);
    long countByEpisodeIdAndStatus(Long episodeId, MarkerStatus status);
    long countByCreatedBy(Long createdBy);
}
