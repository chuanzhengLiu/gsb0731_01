package com.podcast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P0FlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void fullP0Flow_registerLoginPodcastEpisodeMarker() throws Exception {
        // Register -> creates team + admin, returns tokens
        String regBody = """
            {"email":"admin@demo.com","name":"Admin","password":"Passw0rd!ok","teamName":"Demo Team"}
            """;
        String regResp = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(regBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.role", is("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(regResp).get("accessToken").asText();
        String auth = "Bearer " + token;

        // Create podcast
        String podResp = mvc.perform(post("/api/podcasts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Show\",\"type\":\"访谈\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("My Show")))
                .andReturn().getResponse().getContentAsString();
        long podcastId = mapper.readTree(podResp).get("id").asLong();

        // Create episode
        String epResp = mvc.perform(post("/api/podcasts/" + podcastId + "/episodes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":1,\"title\":\"Pilot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PLANNING")))
                .andReturn().getResponse().getContentAsString();

        // List podcasts is team-scoped
        mvc.perform(get("/api/podcasts").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void weakPassword_isRejected() throws Exception {
        String body = """
            {"email":"weak@demo.com","name":"Weak","password":"short","teamName":"T"}
            """;
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        mvc.perform(get("/api/podcasts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossTeamIsolation_isEnforced() throws Exception {
        // Team A
        String aResp = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@demo.com\",\"name\":\"A\",\"password\":\"Passw0rd!ok\",\"teamName\":\"A\"}"))
                .andReturn().getResponse().getContentAsString();
        String aAuth = "Bearer " + mapper.readTree(aResp).get("accessToken").asText();
        String aPod = mvc.perform(post("/api/podcasts").header("Authorization", aAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A Show\",\"type\":\"知识\"}"))
                .andReturn().getResponse().getContentAsString();
        long aPodId = mapper.readTree(aPod).get("id").asLong();

        // Team B tries to read A's podcast -> 404
        String bResp = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@demo.com\",\"name\":\"B\",\"password\":\"Passw0rd!ok\",\"teamName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString();
        String bAuth = "Bearer " + mapper.readTree(bResp).get("accessToken").asText();

        mvc.perform(get("/api/podcasts/" + aPodId).header("Authorization", bAuth))
                .andExpect(status().isNotFound());
    }
}
