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
 * P1 timeline collaboration: time-range markers, status-flow transitions and
 * their role rules (README §4.2 / §9), plus version comparison + marker
 * migration on new uploads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class P1TimelineTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

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

    private long createEpisode(String adminAuth, String team) throws Exception {
        String podResp = mvc.perform(post("/api/podcasts").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + team + "\",\"type\":\"访谈\"}"))
                .andReturn().getResponse().getContentAsString();
        long podcastId = mapper.readTree(podResp).get("id").asLong();
        String epResp = mvc.perform(post("/api/podcasts/" + podcastId + "/episodes").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":1,\"title\":\"E\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(epResp).get("id").asLong();
    }

    private long uploadAudio(String adminAuth, long episodeId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", MP3);
        String avResp = mvc.perform(multipart("/api/episodes/" + episodeId + "/audio-versions").file(file)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(avResp).get("id").asLong();
    }

    @Test
    void createRangeMarker_persistsStartAndEnd() throws Exception {
        String admin = register("p1-a1@demo.com", "P1A");
        long ep = createEpisode(admin, "P1A");
        long av = uploadAudio(admin, ep);

        mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"endTimeMs\":5000,\"type\":\"RE_RECORD\",\"description\":\"补录段\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTimeMs", is(1000)))
                .andExpect(jsonPath("$.endTimeMs", is(5000)));
    }

    @Test
    void rangeMarker_endBeforeStart_rejected() throws Exception {
        String admin = register("p1-a2@demo.com", "P1B");
        long ep = createEpisode(admin, "P1B");
        long av = uploadAudio(admin, ep);

        mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":5000,\"endTimeMs\":1000,\"type\":\"RE_RECORD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusTransition_validAndInvalid() throws Exception {
        String admin = register("p1-a3@demo.com", "P1C");
        long ep = createEpisode(admin, "P1C");
        long av = uploadAudio(admin, ep);
        String created = mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long markerId = mapper.readTree(created).get("id").asLong();

        // PENDING -> IN_PROGRESS (valid)
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // IN_PROGRESS -> RESOLVED (valid)
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        // RESOLVED -> IGNORED (invalid per state machine)
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IGNORED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hostCreatedMarker_statusOnlyChangeableByProducer(CapturedOutput out) throws Exception {
        String admin = register("p1-a4@demo.com", "P1D");
        long ep = createEpisode(admin, "P1D");
        long av = uploadAudio(admin, ep);
        String hostAuth = provisionMember(admin, "p1-host@demo.com", "HOST", out);

        // Host creates their marker
        String created = mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":2000,\"type\":\"FACT_CHECK\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long markerId = mapper.readTree(created).get("id").asLong();

        // Host cannot change its status (主播打的标记只有制作人能改状态)
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", hostAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());

        // Producer/admin can
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void editorWithoutAssignment_cannotChangeStatus(CapturedOutput out) throws Exception {
        String admin = register("p1-a5@demo.com", "P1E");
        long ep = createEpisode(admin, "P1E");
        long av = uploadAudio(admin, ep);
        String created = mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long markerId = mapper.readTree(created).get("id").asLong();

        String editorAuth = provisionMember(admin, "p1-ed@demo.com", "EDITOR", out);
        mvc.perform(patch("/api/markers/" + markerId + "/status").header("Authorization", editorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newUpload_migratesMarkers_andCompareReportsDiff() throws Exception {
        String admin = register("p1-a6@demo.com", "P1F");
        long ep = createEpisode(admin, "P1F");
        long v1 = uploadAudio(admin, ep);

        // Add two markers on v1
        mvc.perform(post("/api/audio-versions/" + v1 + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":1000,\"endTimeMs\":2000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/audio-versions/" + v1 + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTimeMs\":3000,\"type\":\"VOLUME_ISSUE\"}"))
                .andExpect(status().isOk());

        // Upload v2 -> markers should be migrated onto it
        long v2 = uploadAudio(admin, ep);
        mvc.perform(get("/api/audio-versions/" + v2 + "/markers").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Comparison endpoint returns both versions (durations null in test env,
        // so diff is null, but the shape must be correct).
        mvc.perform(get("/api/audio-versions/compare").header("Authorization", admin)
                        .param("from", String.valueOf(v1)).param("to", String.valueOf(v2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromVersionId", is((int) v1)))
                .andExpect(jsonPath("$.toVersionId", is((int) v2)));
    }
}
