package com.podcast.collab.security;

import com.podcast.collab.domain.TeamMember;
import com.podcast.collab.domain.User;
import com.podcast.collab.repo.TeamMemberRepository;
import com.podcast.collab.repo.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TeamMemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   TeamMemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if ("access".equals(claims.get("type"))) {
                    Long userId = Long.valueOf(claims.getSubject());
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        CurrentUser currentUser = buildCurrentUser(user);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                currentUser, null, List.of());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private CurrentUser buildCurrentUser(User user) {
        Long teamId = user.getActiveTeamId();
        com.podcast.collab.domain.Enums.TeamRole role = null;
        if (teamId != null) {
            TeamMember member = memberRepository.findByTeamIdAndUserId(teamId, user.getId()).orElse(null);
            if (member != null) {
                role = member.getRoleInTeam();
            }
        }
        return new CurrentUser(user.getId(), user.getEmail(), user.getName(), teamId, role);
    }
}
