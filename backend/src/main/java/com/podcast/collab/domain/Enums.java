package com.podcast.collab.domain;

public final class Enums {
    private Enums() {}

    public enum SystemRole {
        USER
    }

    public enum TeamRole {
        ADMIN, PRODUCER, EDITOR, OPERATOR, HOST, GUEST
    }

    public enum PodcastType {
        INTERVIEW, NARRATIVE, KNOWLEDGE, NEWS
    }

    public enum EpisodeStatus {
        PLANNING, RECORDING, ROUGH_CUT, FINE_CUT, REVIEW, FINALIZED, DISTRIBUTING, PUBLISHED
    }

    public enum TaskStatus {
        TODO, IN_PROGRESS, DONE, CANCELLED
    }

    public enum MarkerType {
        SLIP, RE_RECORD, VOLUME, BACKGROUND_MUSIC, SFX, TRANSITION, FACT_CHECK
    }

    public enum MarkerStatus {
        PENDING, IN_PROGRESS, RESOLVED, IGNORED
    }

    public enum AssetType {
        AUDIO, TEXT
    }

    public enum DistributionStatus {
        NOT_STARTED, SUBMITTED, UNDER_REVIEW, PUBLISHED, REJECTED
    }
}
