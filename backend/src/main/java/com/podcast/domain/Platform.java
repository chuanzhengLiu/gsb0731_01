package com.podcast.domain;

import jakarta.persistence.*;

/**
 * A distribution platform (README §4.4): 小宇宙、Apple Podcasts、Spotify 等.
 * Seeded at startup; {@code rssRequiredFieldsJson}/{@code categoryOptionsJson}
 * describe platform-specific fields editors fill per episode.
 */
@Entity
@Table(name = "platform")
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "rss_required_fields_json", columnDefinition = "json")
    private String rssRequiredFieldsJson;

    @Column(name = "category_options_json", columnDefinition = "json")
    private String categoryOptionsJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRssRequiredFieldsJson() { return rssRequiredFieldsJson; }
    public void setRssRequiredFieldsJson(String v) { this.rssRequiredFieldsJson = v; }

    public String getCategoryOptionsJson() { return categoryOptionsJson; }
    public void setCategoryOptionsJson(String v) { this.categoryOptionsJson = v; }
}
