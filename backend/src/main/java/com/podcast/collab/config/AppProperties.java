package com.podcast.collab.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Security security = new Security();
    private Audio audio = new Audio();
    private Invite invite = new Invite();
    private Share share = new Share();

    @Data
    public static class Jwt {
        @NotBlank
        private String secret;
        private Duration accessTokenTtl = Duration.ofHours(2);
        private Duration refreshTokenTtl = Duration.ofDays(7);
    }

    @Data
    public static class Storage {
        private String type = "local";
        private String localPath = "./data/storage";
    }

    @Data
    public static class Security {
        private boolean requireHttps = false;
    }

    @Data
    public static class Audio {
        private int maxVersions = 10;
    }

    @Data
    public static class Invite {
        private int ttlHours = 24;
    }

    @Data
    public static class Share {
        private int ttlDays = 7;
    }
}
