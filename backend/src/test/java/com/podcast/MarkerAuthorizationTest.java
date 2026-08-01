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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-based authorization on markers / audio (README §9):
 *  - PRODUCER/ADMIN 可改一切
 *  - EDITOR 只能操作分配给自己的
 *  - OPERATOR 不能碰标记 / 音频
 *  - HOST 可加标记，只能改自己创建的
 *
 * Uploading a version does not require ffmpeg (metadata/waveform just come
 * back empty), so we can exercise the full marker authorization path here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class MarkerAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // Minimal valid MP3 header (ID3) so the file-header validator passes.
    private static final byte[] MP3 = new byte[]{0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00};

    private String register(String email, String team) throws Exception {
        String resp = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"A\",\"password\":\"Passw0rd!ok\",\"teamName\":\"" + team + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    private static String extract(String out, String marker) {
        Matcher m = Pattern.compile(marker + "([A-Za-z0-9_-]+)").matcher(out);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    private String provisionMember(String adminAuth, String email, String role, CapturedOutput out) throws Exception {
        mvc.perform(post("/api/invitations").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"roleInTeam\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        String tok = extract(out.getOut(), "accept-invite\\?token=");
        String resp = mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tok + "\",\"name\":\"" + role + "\",\"password\":\"Member!123ok\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resp).get("accessToken").asText();
    }

    private long[] createEpisodeWithAudio(String adminAuth) throws Exception {
        String podResp = mvc.perform(post("/api/podcasts").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"S\",\"type\":\"访谈\"}"))
                .andReturn().getResponse().getContentAsString();
        long podcastId = mapper.readTree(podResp).get("id").asLong();
        String epResp = mvc.perform(post("/api/podcasts/" + podcastId + "/episodes").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":1,\"title\":\"E\"}"))
                .andReturn().getResponse().getContentAsString();
        long episodeId = mapper.readTree(epResp).get("id").asLong();

        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", MP3);
        String avResp = mvc.perform(multipart("/api/episodes/" + episodeId + "/audio-versions").file(file)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long audioVersionId = mapper.readTree(avResp).get("id").asLong();
        return new long[]{episodeId, audioVersionId};
    }

    @Test
    void operatorCannotCreateMarker(CapturedOutput out) throws Exception {
        String adminAuth = register("mk-a1@demo.com", "MK1");
        long[] ids = createEpisodeWithAudio(adminAuth);
        String operatorAuth = provisionMember(adminAuth, "op1@demo.com", "OPERATOR", out);

        mvc.perform(post("/api/audio-versions/" + ids[1] + "/markers").header("Authorization", operatorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorWithoutAssignmentCannotCreateMarker(CapturedOutput out) throws Exception {
        String adminAuth = register("mk-a2@demo.com", "MK2");
        long[] ids = createEpisodeWithAudio(adminAuth);
        String editorAuth = provisionMember(adminAuth, "mk-ed2@demo.com", "EDITOR", out);

        // Editor has no assigned task on this episode -> forbidden.
        mvc.perform(post("/api/audio-versions/" + ids[1] + "/markers").header("Authorization", editorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void producerCanCreateMarker(CapturedOutput out) throws Exception {
        String adminAuth = register("mk-a3@demo.com", "MK3");
        long[] ids = createEpisodeWithAudio(adminAuth);
        String producerAuth = provisionMember(adminAuth, "prod3@demo.com", "PRODUCER", out);

        mvc.perform(post("/api/audio-versions/" + ids[1] + "/markers").header("Authorization", producerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\",\"description\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("MISSPEAK")));
    }

    @Test
    void hostCanCreateButOnlyEditOwnMarker(CapturedOutput out) throws Exception {
        String adminAuth = register("mk-a4@demo.com", "MK4");
        long[] ids = createEpisodeWithAudio(adminAuth);
        String hostAuth = provisionMember(adminAuth, "host4@demo.com", "HOST", out);

        // Host creates a marker (听审打标记 allowed).
        String created = mvc.perform(post("/api/audio-versions/" + ids[1] + "/markers").header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":2000,\"type\":\"FACT_CHECK\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long hostMarkerId = mapper.readTree(created).get("id").asLong();

        // Host can edit non-status fields of their own marker.
        mvc.perform(put("/api/markers/" + hostMarkerId).header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"主播补充说明\"}"))
                .andExpect(status().isOk());

        // But a host CANNOT change the status of their own marker
        // (README §4.2: 主播打的标记只有制作人能改状态).
        mvc.perform(patch("/api/markers/" + hostMarkerId + "/status").header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());

        // Admin creates another marker; host must NOT be able to edit it.
        String adminMarker = mvc.perform(post("/api/audio-versions/" + ids[1] + "/markers").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":3000,\"type\":\"VOLUME_ISSUE\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long adminMarkerId = mapper.readTree(adminMarker).get("id").asLong();

        mvc.perform(put("/api/markers/" + adminMarkerId).header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
