package com.podcast.web;

import com.podcast.domain.User;
import com.podcast.repository.UserRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Team member listing (README §3.1). Any authenticated team member may view the
 * roster (used to pick task assignees); management (invite/revoke) stays on the
 * admin-only invitation endpoints.
 */
@RestController
@RequestMapping("/api")
public class TeamController {

    private final UserRepository userRepo;

    public TeamController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public record MemberResponse(Long id, String email, String name, String role) {
        static MemberResponse from(User u) {
            return new MemberResponse(u.getId(), u.getEmail(), u.getName(), u.getRole().name());
        }
    }

    @GetMapping("/team/members")
    public List<MemberResponse> members() {
        Long teamId = SecurityUtils.currentTeamId();
        if (teamId == null) {
            throw ApiException.forbidden("当前用户未加入任何团队");
        }
        return userRepo.findByTeamIdOrderByCreatedAtAsc(teamId).stream()
                .map(MemberResponse::from).toList();
    }
}
