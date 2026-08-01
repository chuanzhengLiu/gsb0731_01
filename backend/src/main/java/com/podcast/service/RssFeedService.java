package com.podcast.service;

import com.podcast.domain.*;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.repository.DistributionRepository;
import com.podcast.repository.EpisodeRepository;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates a Podcast RSS 2.0 feed for a podcast (README §4.4 RSS管理). Each
 * published episode with audio becomes an <item> carrying <enclosure> (audio
 * URL + length + type), <itunes:duration> and shownotes in the description.
 */
@Service
public class RssFeedService {

    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final PodcastService podcastService;
    private final EpisodeRepository episodeRepo;
    private final AudioVersionRepository audioRepo;
    private final DistributionRepository distRepo;
    private final SignedUrlService signedUrl;

    public RssFeedService(PodcastService podcastService, EpisodeRepository episodeRepo,
                          AudioVersionRepository audioRepo, DistributionRepository distRepo,
                          SignedUrlService signedUrl) {
        this.podcastService = podcastService;
        this.episodeRepo = episodeRepo;
        this.audioRepo = audioRepo;
        this.distRepo = distRepo;
        this.signedUrl = signedUrl;
    }

    @Transactional(readOnly = true)
    public String generate(Long podcastId, String baseUrl) {
        Podcast podcast = podcastService.get(podcastId); // team isolation
        List<Episode> episodes = episodeRepo.findByPodcastId(podcastId);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">\n");
        sb.append("  <channel>\n");
        sb.append("    <title>").append(xml(podcast.getName())).append("</title>\n");
        sb.append("    <link>").append(xml(baseUrl)).append("</link>\n");
        sb.append("    <language>zh-cn</language>\n");
        sb.append("    <description>").append(xml(podcast.getType() != null ? podcast.getType() + "播客" : "播客"))
                .append("</description>\n");

        for (Episode e : episodes) {
            // Only surface episodes that are published or ready and have audio.
            if (e.getStatus() != EpisodeStatus.PUBLISHED && e.getStatus() != EpisodeStatus.DISTRIBUTING) {
                continue;
            }
            AudioVersion audio = audioRepo.findTopByEpisodeIdOrderByVersionNumberDesc(e.getId()).orElse(null);
            if (audio == null) {
                continue;
            }
            String audioUrl = baseUrl + "/api/media/stream/" + audio.getId()
                    + "?token=" + signedUrl.sign(audio.getId());
            long length = audio.getSizeBytes() != null ? audio.getSizeBytes() : 0L;
            String type = audio.getContentType() != null ? audio.getContentType() : "audio/mpeg";
            String shownotes = shownotesFor(e.getId());

            sb.append("    <item>\n");
            sb.append("      <title>").append(xml("EP" + e.getNumber() + " " + e.getTitle())).append("</title>\n");
            sb.append("      <description>").append(xml(shownotes)).append("</description>\n");
            sb.append("      <guid isPermaLink=\"false\">episode-").append(e.getId()).append("</guid>\n");
            if (e.getRecordDate() != null) {
                sb.append("      <pubDate>")
                        .append(e.getRecordDate().atStartOfDay().atOffset(ZoneOffset.UTC).format(RFC_1123))
                        .append("</pubDate>\n");
            }
            sb.append("      <enclosure url=\"").append(xmlAttr(audioUrl))
                    .append("\" length=\"").append(length)
                    .append("\" type=\"").append(xmlAttr(type)).append("\"/>\n");
            sb.append("      <itunes:duration>").append(fmtDuration(audio.getDurationMs()))
                    .append("</itunes:duration>\n");
            sb.append("    </item>\n");
        }

        sb.append("  </channel>\n");
        sb.append("</rss>\n");
        return sb.toString();
    }

    /** Uses per-episode platform shownotes if present, else the episode theme. */
    private String shownotesFor(Long episodeId) {
        List<Distribution> dists = distRepo.findByEpisodeId(episodeId);
        for (Distribution d : dists) {
            String json = d.getPlatformDataJson();
            if (json != null && json.contains("shownotes")) {
                return json; // raw platform data (contains shownotes)
            }
        }
        return episodeRepo.findById(episodeId).map(Episode::getTheme).orElse("");
    }

    private String fmtDuration(Long ms) {
        if (ms == null || ms <= 0) return "00:00";
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    private String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String xmlAttr(String s) {
        return xml(s).replace("\"", "&quot;");
    }
}
