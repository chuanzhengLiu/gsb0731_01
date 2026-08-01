package com.podcast.collab.repository;

import com.podcast.collab.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    @Query("SELECT t FROM Team t JOIN TeamMember tm ON t.id = tm.teamId WHERE tm.userId = :userId")
    List<Team> findTeamsByUserId(@Param("userId") Long userId);
}
