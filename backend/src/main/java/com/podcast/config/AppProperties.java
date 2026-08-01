package com.podcast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Strongly-typed application properties (prefix: app). */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Storage storage = new Storage();
    private final Audio audio = new Audio();
    private final Media media = new Media();
    private final RateLimit rateLimit = new RateLimit();
    private final Frontend frontend = new Frontend();
    private final Invitation invitation = new Invitation();
    private final PasswordReset passwordReset = new PasswordReset();
    private final Transcription transcription = new Transcription();
    private final Share share = new Share();

    public Jwt getJwt() { return jwt; }
    public Storage getStorage() { return storage; }
    public Audio getAudio() { return audio; }
    public Media getMedia() { return media; }
    public RateLimit getRateLimit() { return rateLimit; }
    public Frontend getFrontend() { return frontend; }
    public Invitation getInvitation() { return invitation; }
    public PasswordReset getPasswordReset() { return passwordReset; }
    public Transcription getTranscription() { return transcription; }
    public Share getShare() { return share; }

    public static class Jwt {
        private String secret;
        private long accessTokenTtlSeconds = 7200;
        private long refreshTokenTtlSeconds = 604800;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTokenTtlSeconds() { return accessTokenTtlSeconds; }
        public void setAccessTokenTtlSeconds(long v) { this.accessTokenTtlSeconds = v; }
        public long getRefreshTokenTtlSeconds() { return refreshTokenTtlSeconds; }
        public void setRefreshTokenTtlSeconds(long v) { this.refreshTokenTtlSeconds = v; }
    }

    public static class Storage {
        private String root = "./data/storage";
        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
    }

    public static class Audio {
        private long maxSizeBytes = 524288000L;
        private String allowedExtensions = "wav,mp3,m4a";
        public long getMaxSizeBytes() { return maxSizeBytes; }
        public void setMaxSizeBytes(long v) { this.maxSizeBytes = v; }
        public String getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(String v) { this.allowedExtensions = v; }
    }

    public static class Media {
        private String urlSigningSecret;
        private long urlTtlSeconds = 3600;
        public String getUrlSigningSecret() { return urlSigningSecret; }
        public void setUrlSigningSecret(String v) { this.urlSigningSecret = v; }
        public long getUrlTtlSeconds() { return urlTtlSeconds; }
        public void setUrlTtlSeconds(long v) { this.urlTtlSeconds = v; }
    }

    public static class RateLimit {
        private int generalPerMinute = 100;
        private int loginPerMinute = 5;
        private int uploadPerMinute = 10;
        public int getGeneralPerMinute() { return generalPerMinute; }
        public void setGeneralPerMinute(int v) { this.generalPerMinute = v; }
        public int getLoginPerMinute() { return loginPerMinute; }
        public void setLoginPerMinute(int v) { this.loginPerMinute = v; }
        public int getUploadPerMinute() { return uploadPerMinute; }
        public void setUploadPerMinute(int v) { this.uploadPerMinute = v; }
    }

    public static class Frontend {
        // Base URL used to build invitation / password-reset links.
        private String baseUrl = "http://localhost";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
    }

    public static class Invitation {
        private long ttlSeconds = 86400;   // README §3.2: 邀请链接 24 小时有效
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long v) { this.ttlSeconds = v; }
    }

    public static class PasswordReset {
        private long ttlSeconds = 1800;    // README §3.2: 密码找回链接 30 分钟有效
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long v) { this.ttlSeconds = v; }
    }

    /**
     * Transcription settings (README §4.3). Provider selects the backend:
     *  - "openai"   : OpenAI Whisper API, key from env (never hardcoded)
     *  - "local"    : local Whisper model invocation (if deployed)
     *  - "auto"     : use openai when an api-key is present, else degrade
     * When no provider is usable, the service falls back to a deterministic
     * offline stub so the feature stays runnable without a key.
     */
    public static class Transcription {
        private String provider = "auto";
        private String apiKey;                 // from env WHISPER_API_KEY / OPENAI_API_KEY
        private String apiBaseUrl = "https://api.openai.com/v1";
        private String model = "whisper-1";
        private String language = "zh";

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String v) { this.apiBaseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getLanguage() { return language; }
        public void setLanguage(String v) { this.language = v; }
    }

    /** Guest share links (README §3.1/§8): random token, 7-day expiry. */
    public static class Share {
        private long ttlSeconds = 604800;      // 7 days
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long v) { this.ttlSeconds = v; }
    }
}
