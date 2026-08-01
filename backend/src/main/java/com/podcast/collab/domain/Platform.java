package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "platforms")
@Getter
@Setter
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "rss_required_fields_json", columnDefinition = "json")
    private String rssRequiredFieldsJson;

    @Column(name = "category_options_json", columnDefinition = "json")
    private String categoryOptionsJson;
}
