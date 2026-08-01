package com.podcast.collab.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid token"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
    TEAM_MISMATCH(HttpStatus.FORBIDDEN, "Cross-team access is forbidden"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    BAD_FILE(HttpStatus.BAD_REQUEST, "Invalid file"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds maximum size"),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST, "Invitation expired or already used"),
    SHARE_EXPIRED(HttpStatus.BAD_REQUEST, "Share link expired"),
    PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "Password does not meet policy"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
