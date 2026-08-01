package com.podcast.service;

import com.podcast.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails. In this environment no SMTP is configured, so
 * the message (and the actionable link) is written to the application log.
 * The link generation / expiry / verification logic lives in the callers;
 * this class only delivers the message, so swapping in a real mailer later
 * touches nothing else.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final String frontendBaseUrl;

    public EmailService(AppProperties props) {
        this.frontendBaseUrl = props.getFrontend().getBaseUrl();
    }

    /** Sends a team-invitation email carrying the 24h invite link. */
    public void sendInvitation(String toEmail, String teamName, String rawToken) {
        String link = frontendBaseUrl + "/accept-invite?token=" + rawToken;
        log.info("[EMAIL:INVITATION] to={} team={} link={} (24小时内有效)", toEmail, teamName, link);
    }

    /** Sends a password-reset email carrying the 30min reset link. */
    public void sendPasswordReset(String toEmail, String rawToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        log.info("[EMAIL:PASSWORD_RESET] to={} link={} (30分钟内有效)", toEmail, link);
    }
}
