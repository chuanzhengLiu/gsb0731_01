package com.podcast.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Marker status (README §4.2): 待处理/处理中/已解决/已忽略.
 * Defines the allowed status-flow transitions (README §4.2 标记状态流转):
 *   PENDING     → IN_PROGRESS, RESOLVED, IGNORED
 *   IN_PROGRESS → RESOLVED, IGNORED, PENDING
 *   RESOLVED    → IN_PROGRESS (重新打开), PENDING
 *   IGNORED     → PENDING (重新打开)
 */
public enum MarkerStatus {
    PENDING,     // 待处理
    IN_PROGRESS, // 处理中
    RESOLVED,    // 已解决
    IGNORED;     // 已忽略

    private static final Map<MarkerStatus, Set<MarkerStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(IN_PROGRESS, RESOLVED, IGNORED),
            IN_PROGRESS, EnumSet.of(RESOLVED, IGNORED, PENDING),
            RESOLVED, EnumSet.of(IN_PROGRESS, PENDING),
            IGNORED, EnumSet.of(PENDING)
    );

    /** Whether transitioning from this status to {@code target} is permitted. */
    public boolean canTransitionTo(MarkerStatus target) {
        if (this == target) {
            return true; // no-op is allowed
        }
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
