package com.podcast.collab.controller;

import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** 生成符合 Podcast RSS 2.0 标准的 feed（含 enclosure、duration、shownotes） */
@RestController
@RequestMapping("/api/rss")
@RequiredArgsConstructor
public class RssController {
    private final PodcastRepository podcastRepository;
    private final EpisodeRepository episodeRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final JwtService jwtService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;
    @Value("${app.rss-enclosure-ttl-minutes}")
    private long enclosureTtlMinutes;

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /**
     * 公开 feed：Apple Podcasts、小宇宙等平台抓取时不带任何凭证。
     * 仅暴露"分发中/已发布"的单集，其余制作数据不出团队。
     */
    @GetMapping(value = "/podcasts/{podcastId}", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> feed(@PathVariable Long podcastId) {
        Podcast podcast = podcastRepository.findById(podcastId)
                .orElseThrow(() -> new IllegalArgumentException("节目不存在"));
        List<Episode> episodes = episodeRepository.findByPodcastIdOrderByNumberDesc(podcastId).stream()
                .filter(e -> e.getStatus() == EpisodeStatus.PUBLISHED || e.getStatus() == EpisodeStatus.DISTRIBUTING)
                .toList();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" ")
                .append("xmlns:content=\"http://purl.org/rss/1.0/modules/content/\">\n<channel>\n");
        xml.append("<title>").append(escape(podcast.getName())).append("</title>\n");
        xml.append("<link>").append(escape(publicBaseUrl)).append("/podcasts/")
                .append(podcastId).append("</link>\n");
        xml.append("<description>").append(escape(podcast.getName())).append("</description>\n");
        xml.append("<language>zh-CN</language>\n");

        for (Episode ep : episodes) {
            xml.append("<item>\n");
            xml.append("<title>第").append(ep.getNumber()).append("期 ")
                    .append(escape(ep.getTitle())).append("</title>\n");
            xml.append("<guid isPermaLink=\"false\">episode-").append(ep.getId()).append("</guid>\n");
            if (ep.getTheme() != null) {
                xml.append("<description>").append(escape(ep.getTheme())).append("</description>\n");
                xml.append("<content:encoded>").append(escape(ep.getTheme())).append("</content:encoded>\n");
            }
            if (ep.getUpdatedAt() != null) {
                xml.append("<pubDate>")
                        .append(ep.getUpdatedAt().atOffset(ZoneOffset.UTC).format(RFC_822))
                        .append("</pubDate>\n");
            }
            audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(ep.getId())
                    .ifPresent(v -> {
                        if (ep.getFinalAudioUrl() != null) {
                            xml.append("<enclosure url=\"")
                                    .append(escape(enclosureUrl(ep, v)))
                                    .append("\" type=\"").append(mimeType(v.getFileUrl()))
                                    .append("\" length=\"")
                                    .append(v.getFileSize() == null ? 0 : v.getFileSize())
                                    .append("\"/>\n");
                        }
                        if (v.getDurationMs() != null) {
                            long totalSec = v.getDurationMs() / 1000;
                            xml.append("<itunes:duration>")
                                    .append(String.format("%02d:%02d:%02d",
                                            totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60))
                                    .append("</itunes:duration>\n");
                        }
                    });
            xml.append("</item>\n");
        }
        xml.append("</channel>\n</rss>");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/rss+xml;charset=UTF-8"))
                .body(xml.toString());
    }

    /** enclosure 地址：相对路径补全为完整签名 URL（长期有效签名供平台抓取） */
    private String enclosureUrl(Episode ep, AudioVersion version) {
        String url = ep.getFinalAudioUrl();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url; // 已是完整外链
        }
        // 从 "/api/audio/stream/{versionId}" 解析版本 ID 并签名
        Long versionId = url.startsWith("/api/audio/stream/")
                ? Long.parseLong(url.substring("/api/audio/stream/".length()))
                : version.getId();
        long exp = System.currentTimeMillis() / 1000 + enclosureTtlMinutes * 60;
        String sig = jwtService.signAudioUrl(versionId, exp);
        return publicBaseUrl + "/api/audio/stream/" + versionId + "?exp=" + exp + "&sig=" + sig;
    }

    /** 按真实音频文件扩展名输出 MIME 类型（fileUrl 带原始扩展名） */
    private String mimeType(String fileUrl) {
        String lower = fileUrl == null ? "" : fileUrl.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
