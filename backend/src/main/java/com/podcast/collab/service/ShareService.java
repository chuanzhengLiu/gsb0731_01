package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import com.podcast.collab.domain.Episode;
import com.podcast.collab.domain.ShareAccessLog;
import com.podcast.collab.domain.ShareLink;
import com.podcast.collab.dto.StatsDtos.ShareLinkResponse;
import com.podcast.collab.repo.ShareAccessLogRepository;
import com.podcast.collab.repo.ShareLinkRepository;
import com.podcast.collab.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

@Service
public class ShareService {

    private final ShareLinkRepository shareRepo;
    private final ShareAccessLogRepository accessRepo;
    private final TeamGuard teamGuard;
    private final AppProperties props;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public ShareService(ShareLinkRepository shareRepo, ShareAccessLogRepository accessRepo,
                        TeamGuard teamGuard, AppProperties props, AuditService auditService) {
        this.shareRepo = shareRepo;
        this.accessRepo = accessRepo;
        this.teamGuard = teamGuard;
        this.props = props;
        this.auditService = auditService;
    }

    @Transactional
    public ShareLinkResponse create(Long episodeId, CurrentUser user) {
        Episode e = teamGuard.requireEpisode(episodeId, user);
        ShareLink link = new ShareLink();
        link.setEpisodeId(e.getId());
        link.setToken(randomToken(24));
        link.setCreatedBy(user.id());
        link.setExpiresAt(Instant.now().plus(props.getShare().getTtlDays(), ChronoUnit.DAYS));
        link.setCreatedAt(Instant.now());
        shareRepo.save(link);
        auditService.log(user, "SHARE_CREATE", "ShareLink", link.getId(),
                Map.of("episodeId", episodeId));
        return toResponse(link);
    }

    @Transactional
    public void revoke(Long linkId, CurrentUser user) {
        ShareLink link = shareRepo.findById(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(link.getEpisodeId(), user);
        link.setExpiresAt(Instant.now());
        shareRepo.save(link);
        auditService.log(user, "SHARE_REVOKE", "ShareLink", linkId, null);
    }

    @Transactional
    public ShareLink verify(String token, HttpServletRequest req) {
        ShareLink link = shareRepo.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_EXPIRED, "Share link not found"));
        if (link.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.SHARE_EXPIRED);
        }
        ShareAccessLog log = new ShareAccessLog();
        log.setShareLinkId(link.getId());
        log.setIpAddress(clientIp(req));
        log.setUserAgent(req != null ? truncate(req.getHeader("User-Agent"), 255) : null);
        log.setAccessedAt(Instant.now());
        accessRepo.save(log);
        return link;
    }

    private ShareLinkResponse toResponse(ShareLink link) {
        return new ShareLinkResponse(link.getId(), link.getEpisodeId(), link.getToken(),
                link.getExpiresAt(), "/share/" + link.getToken(), link.getCreatedAt());
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
