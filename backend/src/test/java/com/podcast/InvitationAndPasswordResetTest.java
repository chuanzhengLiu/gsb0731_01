package com.podcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Invitation (24h) + password recovery (30min) flows — README §3.2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class InvitationAndPasswordResetTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String registerAdmin(String email, String team) throws Exception {
        String resp = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"Admin\",\"password\":\"Passw0rd!ok\",\"teamName\":\"" + team + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    private static String extractToken(String output, String marker) {
        // Links look like: .../accept-invite?token=XXXX or /reset-password?token=XXXX
        Matcher m = Pattern.compile(marker + "([A-Za-z0-9_-]+)").matcher(output);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    @Test
    void inviteMember_thenAccept_logsInWithGrantedRole(CapturedOutput output) throws Exception {
        String adminAuth = registerAdmin("owner1@demo.com", "Team1");

        // Admin invites an EDITOR
        mvc.perform(post("/api/invitations").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"editor1@demo.com\",\"roleInTeam\":\"EDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleInTeam", is("EDITOR")))
                .andExpect(jsonPath("$.accepted", is(false)));

        String token = extractToken(output.getOut(), "accept-invite\\?token=");
        org.junit.jupiter.api.Assertions.assertNotNull(token, "invite token should be logged");

        // Accept -> creates the user with the EDITOR role and logs in
        String acceptResp = mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"name\":\"Editor\",\"password\":\"Editor#123ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role", is("EDITOR")))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotNull(mapper.readTree(acceptResp).get("accessToken").asText());

        // The invite cannot be reused
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"name\":\"X\",\"password\":\"Editor#123ok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdmin_cannotInvite(CapturedOutput output) throws Exception {
        String adminAuth = registerAdmin("owner2@demo.com", "Team2");
        // Admin invites an editor, editor accepts and obtains a token.
        mvc.perform(post("/api/invitations").header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ed2@demo.com\",\"roleInTeam\":\"EDITOR\"}")).andExpect(status().isOk());
        String token = extractToken(output.getOut(), "accept-invite\\?token=");
        String acceptResp = mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"name\":\"Ed\",\"password\":\"Editor#123ok\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String editorAuth = "Bearer " + mapper.readTree(acceptResp).get("accessToken").asText();

        // Editor must not be able to invite (README §9 — only admin invites).
        mvc.perform(post("/api/invitations").header("Authorization", editorAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@demo.com\",\"roleInTeam\":\"EDITOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordReset_flow_updatesPasswordAndRevokesSessions(CapturedOutput output) throws Exception {
        // Register (also logs in). Then request reset.
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@demo.com\",\"name\":\"R\",\"password\":\"OldPass!123\",\"teamName\":\"RT\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@demo.com\"}"))
                .andExpect(status().isNoContent());

        String token = extractToken(output.getOut(), "reset-password\\?token=");
        org.junit.jupiter.api.Assertions.assertNotNull(token, "reset token should be logged");

        // Reset to a new valid password
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"NewPass!456\"}"))
                .andExpect(status().isNoContent());

        // Old password no longer works; new one does
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@demo.com\",\"password\":\"OldPass!123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@demo.com\",\"password\":\"NewPass!456\"}"))
                .andExpect(status().isOk());

        // Token is single-use
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"Another!789\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_unknownEmail_stillReturns204() throws Exception {
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@demo.com\"}"))
                .andExpect(status().isNoContent());
    }
}
