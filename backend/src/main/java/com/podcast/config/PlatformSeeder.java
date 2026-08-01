package com.podcast.config;

import com.podcast.domain.Platform;
import com.podcast.repository.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the built-in distribution platforms (README §4.4) on startup if the
 * table is empty. Platforms are global (shared across teams); each team keeps
 * its own {@code platform_account} rows against these.
 */
@Component
public class PlatformSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformSeeder.class);

    private final PlatformRepository repo;

    public PlatformSeeder(PlatformRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        List<String> names = List.of(
                "小宇宙", "Apple Podcasts", "Spotify", "网易云音乐", "喜马拉雅");
        for (String name : names) {
            Platform p = new Platform();
            p.setName(name);
            // Minimal per-platform field hints; editors fill these per episode.
            p.setRssRequiredFieldsJson("[\"title\",\"shownotes\",\"duration\",\"enclosure\"]");
            p.setCategoryOptionsJson("[\"知识\",\"访谈\",\"叙事\",\"新闻\"]");
            repo.save(p);
        }
        log.info("Seeded {} distribution platforms", names.size());
    }
}
