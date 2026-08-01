package com.podcast.service;

import com.podcast.domain.*;
import com.podcast.repository.*;
import com.podcast.config.AppProperties;
import com.podcast.security.SecurityUtils;
import com.podcast.security.TokenUtil;
import com.podcast.web.ApiException;
import com.podcast.web.dto.ShareDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Guest share links (README §3.1/§8): a producer creates a random-token link
 * for an episode (7-day expiry); guests open it (no login) to see a read-only
 * view. Every access is logged. Tokens are stored hashed.
 */
@Service
public class ShareService {

    private final ShareLinkRepository shareRepo;
    private final ShareAccessLogRepository accessLogRepo;
    private final EpisodeRepository episodeRepo;
    private final PodcastRepository podcastRepo;
    private final AudioVersionRepository audioRepo;
    private final TimelineMarkerRepository markerRepo;
    private final TranscriptSegmentRepository segmentRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final SignedUrlService signedUrl;
    private final AuditService audit;
    private final AppProperties props;

    public ShareService(ShareLinkRepository shareRepo, ShareAccessLogRepository accessLogRepo,
                        EpisodeRepository episodeRepo, PodcastRepository podcastRepo,
                        AudioVersionRepository audioRepo, TimelineMarkerRepository markerRepo,
                        TranscriptSegmentRepository segmentRepo, AccessGuard accessGuard,
                        AuthorizationService authz, SignedUrlService signedUrl,
                        AuditService audit, AppProperties props) {
        this.shareRepo = shareRepo;
        this.accessLogRepo = accessLogRepo;
        this.episodeRepo = episodeRepo;
        this.podcastRepo = podcastRepo;
        this.audioRepo = audioRepo;
        this.markerRepo = markerRepo;
        this.segmentRepo = segmentRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.signedUrl = signedUrl;
        this.audit = audit;
        this.props = props;
    }

    /** Raw token is returned once (in the link); only its hash is stored. */
    public record CreatedShare(ShareLink link, String rawToken) {}

    @Transactional
    public CreatedShare create(Long episodeId) {
        Episode e = accessGuard.requireEpisode(episodeId); // team isolation
        authz.checkCanManageTasks(); // producer-level creates share links
        Long teamId = accessGuard.requireTeamId();

        String rawToken = TokenUtil.generateRawToken();
        ShareLink link = new ShareLink();
        link.setTeamId(teamId);
        link.setEpisodeId(e.getId());
        link.setTokenHash(TokenUtil.sha256(rawToken));
        link.setCreatedBy(SecurityUtils.currentUserId());
        link.setExpiresAt(Instant.now().plusSeconds(props.getShare().getTtlSeconds()));
        link = shareRepo.save(link);
        audit.log("SHARE_LINK_CREATE", "ShareLink", link.getId(), "episode=" + episodeId);
        return new CreatedShare(link, rawToken);
    }

    public String urlFor(String rawToken) {
        return props.getFrontend().getBaseUrl() + "/share?token=" + rawToken;
    }

    /** Placeholder URL for listing existing links (raw token not re-derivable). */
    public String maskedUrl(ShareLink link) {
        return props.getFrontend().getBaseUrl() + "/share?token=***";
    }

    public boolean isExpired(ShareLink link) {
        return link.getExpiresAt().isBefore(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ShareLink> listForEpisode(Long episodeId) {
        accessGuard.requireEpisode(episodeId);
        return shareRepo.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
    }

    @Transactional
    public void revoke(Long shareLinkId) {
        Long teamId = accessGuard.requireTeamId();
        ShareLink link = shareRepo.findById(shareLinkId)
                .orElseThrow(() -> ApiException.notFound("分享链接不存在"));
        if (!teamId.equals(link.getTeamId())) {
            throw ApiException.notFound("分享链接不存在"); // don't leak cross-team
        }
        authz.checkCanManageTasks();
        link.setRevoked(true);
        shareRepo.save(link);
        audit.log("SHARE_LINK_REVOKE", "ShareLink", link.getId(), null);
    }

    // ---------- Public (guest) access — token only, no session ----------

    private ShareLink resolveValid(String rawToken) {
        ShareLink link = shareRepo.findByTokenHash(TokenUtil.sha256(rawToken))
                .orElseThrow(() -> ApiException.notFound("分享链接无效"));
        if (link.isRevoked()) {
            throw ApiException.forbidden("分享链接已被撤销");
        }
        if (isExpired(link)) {
            throw ApiException.forbidden("分享链接已过期");
        }
        return link;
    }

    /**
     * Resolves a shared episode view for a guest and logs the access
     * (README §8 访问记录日志). No team-session context is required.
     */
    @Transactional
    public SharedEpisodeView viewShared(String rawToken, HttpServletRequest http) {
        ShareLink link = resolveValid(rawToken);

        // Record the access.
        ShareAccessLog logEntry = new ShareAccessLog();
        logEntry.setShareLinkId(link.getId());
        logEntry.setIpAddress(clientIp(http));
        logEntry.setUserAgent(truncate(http.getHeader("User-Agent"), 255));
        accessLogRepo.save(logEntry);
        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(Instant.now());
        shareRepo.save(link);

        Episode e = episodeRepo.findById(link.getEpisodeId())
                .orElseThrow(() -> ApiException.notFound("单集不存在"));
        Podcast podcast = podcastRepo.findById(e.getPodcastId()).orElse(null);

        // Latest online (non-archived) version for playback.
        AudioVersion av = audioRepo.findByEpisodeIdOrderByVersionNumberDesc(e.getId()).stream()
                .filter(v -> !v.isArchived())
                .findFirst().orElse(null);

        AudioView audioView = null;
        List<MarkerView> markerViews = List.of();
        List<SegmentView> segmentViews = List.of();
        if (av != null) {
            String streamUrl = "/api/media/stream/" + av.getId() + "?token=" + signedUrl.sign(av.getId());
            audioView = new AudioView(av.getId(), av.getVersionNumber(), av.getDurationMs(), streamUrl);
            markerViews = markerRepo.findByAudioVersionIdOrderByStartTimeMsAsc(av.getId()).stream()
                    .map(m -> new MarkerView(m.getId(), m.getStartTimeMs(), m.getEndTimeMs(),
                            m.getType().name(), m.getDescription(), m.getStatus().name()))
                    .toList();
            segmentViews = segmentRepo.findByAudioVersionIdOrderBySegmentIndexAsc(av.getId()).stream()
                    .map(s -> new SegmentView(s.getId(), s.getStartTimeMs(), s.getEndTimeMs(),
                            s.getText(), s.getSpeaker(), s.getSpeakerColor()))
                    .toList();
        }

        return new SharedEpisodeView(
                e.getId(), e.getNumber(), e.getTitle(), e.getTheme(), e.getStatus().name(),
                podcast != null ? podcast.getName() : null,
                audioView, markerViews, segmentViews);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
