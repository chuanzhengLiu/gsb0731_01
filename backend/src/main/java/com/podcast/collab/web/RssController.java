package com.podcast.collab.web;

import com.podcast.collab.domain.AudioVersion;
import com.podcast.collab.domain.Enums;
import com.podcast.collab.domain.Episode;
import com.podcast.collab.domain.Podcast;
import com.podcast.collab.repo.AudioVersionRepository;
import com.podcast.collab.repo.EpisodeRepository;
import com.podcast.collab.repo.PodcastRepository;
import com.podcast.collab.service.SignedUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@RestController
public class RssController {

    private static final DateTimeFormatter RFC822 = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Set<Enums.EpisodeStatus> PUBLISHABLE = Set.of(
            Enums.EpisodeStatus.FINALIZED,
            Enums.EpisodeStatus.DISTRIBUTING,
            Enums.EpisodeStatus.PUBLISHED);

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;
    private final AudioVersionRepository versionRepo;
    private final SignedUrlService signedUrlService;

    public RssController(PodcastRepository podcastRepo, EpisodeRepository episodeRepo,
                         AudioVersionRepository versionRepo, SignedUrlService signedUrlService) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
        this.versionRepo = versionRepo;
        this.signedUrlService = signedUrlService;
    }

    @GetMapping(value = "/rss/podcasts/{podcastId}", produces = MediaType.APPLICATION_XML_VALUE)
    public String feed(@PathVariable Long podcastId, HttpServletRequest req) {
        Podcast podcast = podcastRepo.findById(podcastId).orElseThrow();
        List<Episode> episodes = episodeRepo.findByPodcastIdOrderByNumberDesc(podcastId);
        String baseUrl = resolveBaseUrl(req);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">\n");
        xml.append("<channel>\n");
        xml.append("<title>").append(escape(podcast.getName())).append("</title>\n");
        xml.append("<link>").append(baseUrl).append("</link>\n");
        xml.append("<language>zh-cn</language>\n");

        for (Episode ep : episodes) {
            // Only publish finalized (and later) episodes that have a designated final audio.
            if (!PUBLISHABLE.contains(ep.getStatus()) || ep.getFinalAudioUrl() == null) {
                continue;
            }
            AudioVersion finalVersion = versionRepo.findFirstByFileUrl(ep.getFinalAudioUrl()).orElse(null);
            if (finalVersion == null || finalVersion.isArchived()) {
                continue;
            }

            xml.append("<item>\n");
            xml.append("<title>").append(escape(ep.getNumber() + ". " + ep.getTitle())).append("</title>\n");
            if (ep.getTheme() != null) {
                xml.append("<description>").append(escape(ep.getTheme())).append("</description>\n");
            }
            xml.append("<guid isPermaLink=\"false\">episode-").append(ep.getId()).append("</guid>\n");
            xml.append("<pubDate>").append(RFC822.format(ZonedDateTime.ofInstant(ep.getCreatedAt(), ZoneOffset.UTC))).append("</pubDate>\n");
            String enclosureUrl = signedUrlService.signRssEnclosure(baseUrl + finalVersion.getFileUrl());
            xml.append("<enclosure url=\"").append(escape(enclosureUrl))
                    .append("\" length=\"").append(finalVersion.getFileSize())
                    .append("\" type=\"").append(escape(finalVersion.getMimeType())).append("\"/>\n");
            xml.append("<itunes:duration>").append(formatDuration(finalVersion.getDurationMs())).append("</itunes:duration>\n");
            xml.append("</item>\n");
        }

        xml.append("</channel>\n</rss>");
        return xml.toString();
    }

    private String resolveBaseUrl(HttpServletRequest req) {
        String scheme = req.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = req.getScheme();
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null) host = req.getHeader("Host");
        if (host == null) host = req.getServerName() + ":" + req.getServerPort();
        return scheme + "://" + host;
    }

    private String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
