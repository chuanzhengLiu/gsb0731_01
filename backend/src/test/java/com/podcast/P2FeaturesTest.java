package com.podcast;

import com.fasterxml.jackson.databind.JsonNode;
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
 * P1 transcription follow-up + P2 modules (README §4.1/§4.3–§4.6, §3.1):
 * transcript generation/edit/markers, task board + assignment-based editor
 * rights, distribution status + operator-only rule, RSS, assets + usage,
 * team stats, and guest share links.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class P2FeaturesTest {

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

    private long memberId(String adminAuth, String memberAuth) throws Exception {
        // The member's own user id is embedded in their token payload via /team/members;
        // easier: list members and find by role differences. Here we return the last member.
        String resp = mvc.perform(get("/api/team/members").header("Authorization", adminAuth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode arr = mapper.readTree(resp);
        return arr.get(arr.size() - 1).get("id").asLong();
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
    void transcriptGeneratedOnUpload_andEditable() throws Exception {
        String admin = register("p2-t1@demo.com", "P2T1");
        long ep = createEpisode(admin, "P2T1");
        long av = uploadAudio(admin, ep);

        // Offline stub produces segments automatically on upload.
        String resp = mvc.perform(get("/api/audio-versions/" + av + "/transcript").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andReturn().getResponse().getContentAsString();
        long segId = mapper.readTree(resp).get(0).get("id").asLong();

        // Edit a segment's text (README §4.3): should mark edited=true.
        mvc.perform(put("/api/transcript-segments/" + segId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"人工修正后的文本\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited", is(true)))
                .andExpect(jsonPath("$.text", is("人工修正后的文本")));

        // Add a marker from a transcript segment.
        mvc.perform(post("/api/transcript-segments/" + segId + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"FACT_CHECK\",\"asRange\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTimeMs", notNullValue()));
    }

    @Test
    void taskBoard_assignmentEnablesEditorMarkerRights(CapturedOutput out) throws Exception {
        String admin = register("p2-tb@demo.com", "P2TB");
        long ep = createEpisode(admin, "P2TB");
        long av = uploadAudio(admin, ep);
        String editorAuth = provisionMember(admin, "p2-ed@demo.com", "EDITOR", out);
        long editorId = memberId(admin, editorAuth);

        // Before assignment, the editor cannot create a marker on this episode.
        mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", editorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isForbidden());

        // Producer assigns a task to the editor for this episode.
        mvc.perform(post("/api/episodes/" + ep + "/tasks").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"补录\",\"assigneeId\":" + editorId + "}"))
                .andExpect(status().isOk());

        // Now the editor may create a marker (剪辑师只能操作分配给自己的单集).
        mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", editorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void distribution_operatorOnly_andStatusTracking(CapturedOutput out) throws Exception {
        String admin = register("p2-d1@demo.com", "P2D1");
        long ep = createEpisode(admin, "P2D1");
        String editorAuth = provisionMember(admin, "p2-d-ed@demo.com", "EDITOR", out);
        String operatorAuth = provisionMember(admin, "p2-d-op@demo.com", "OPERATOR", out);

        // Pick a platform id.
        String plResp = mvc.perform(get("/api/platforms").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long platformId = mapper.readTree(plResp).get(0).get("id").asLong();

        // Editor cannot manage distribution (运营/制作人 only).
        mvc.perform(post("/api/episodes/" + ep + "/distributions").header("Authorization", editorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platformId\":" + platformId + "}"))
                .andExpect(status().isForbidden());

        // Operator can create + advance status.
        String dResp = mvc.perform(post("/api/episodes/" + ep + "/distributions").header("Authorization", operatorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platformId\":" + platformId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_STARTED")))
                .andReturn().getResponse().getContentAsString();
        long distId = mapper.readTree(dResp).get("id").asLong();

        mvc.perform(patch("/api/distributions/" + distId + "/status").header("Authorization", operatorAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")));
    }

    @Test
    void rssFeed_containsEnclosureAndDuration() throws Exception {
        String admin = register("p2-rss@demo.com", "P2RSS");
        String podResp = mvc.perform(post("/api/podcasts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"RSSCast\",\"type\":\"知识\"}"))
                .andReturn().getResponse().getContentAsString();
        long podcastId = mapper.readTree(podResp).get("id").asLong();
        // Episode must be PUBLISHED/DISTRIBUTING with audio to surface in the feed.
        String epResp = mvc.perform(post("/api/podcasts/" + podcastId + "/episodes").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":1,\"title\":\"首发\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        long ep = mapper.readTree(epResp).get("id").asLong();
        uploadAudio(admin, ep);

        String xml = mvc.perform(get("/api/podcasts/" + podcastId + "/rss").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<rss version=\"2.0\""));
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<enclosure"));
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("itunes:duration"));
    }

    @Test
    void assetLibrary_textAsset_andUsageTracking() throws Exception {
        String admin = register("p2-as@demo.com", "P2AS");
        long ep = createEpisode(admin, "P2AS");

        String aResp = mvc.perform(post("/api/assets/text").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"赞助口播\",\"category\":\"口播文案\",\"textContent\":\"感谢赞助商\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("TEXT")))
                .andReturn().getResponse().getContentAsString();
        long assetId = mapper.readTree(aResp).get("id").asLong();

        // Record a usage; usageCount should increment.
        mvc.perform(post("/api/assets/" + assetId + "/usages").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"episodeId\":" + ep + ",\"positionMs\":5000,\"note\":\"片头\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/assets").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usageCount", is(1)));
    }

    @Test
    void teamStats_reportsEpisodeAndMemberMetrics() throws Exception {
        String admin = register("p2-st@demo.com", "P2ST");
        long ep = createEpisode(admin, "P2ST");
        long av = uploadAudio(admin, ep);
        mvc.perform(post("/api/audio-versions/" + av + "/markers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startTimeMs\":1000,\"type\":\"MISSPEAK\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/stats/team").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes", hasSize(1)))
                .andExpect(jsonPath("$.episodes[0].markerCount", is(1)))
                .andExpect(jsonPath("$.members", hasSize(greaterThan(0))));
    }

    @Test
    void shareLink_createsAndGuestViewsWithinExpiry() throws Exception {
        String admin = register("p2-sh@demo.com", "P2SH");
        long ep = createEpisode(admin, "P2SH");
        uploadAudio(admin, ep);

        String sResp = mvc.perform(post("/api/episodes/" + ep + "/share-links").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString("/share?token=")))
                .andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(sResp).get("url").asText();
        String token = url.substring(url.indexOf("token=") + 6);

        // Guest view (no auth header) — whitelisted, validated by token.
        mvc.perform(get("/api/share/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodeId", is((int) ep)))
                .andExpect(jsonPath("$.audio", notNullValue()));

        // An invalid token is rejected.
        mvc.perform(get("/api/share/not-a-real-token"))
                .andExpect(status().isNotFound());
    }
}
