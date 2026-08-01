package com.podcast.web;

import com.podcast.service.DistributionService;
import com.podcast.service.RssFeedService;
import com.podcast.web.dto.DistributionDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Distribution management endpoints (README §4.4). Write operations are gated
 * to OPERATOR/producer-level in the service (README §9 运营只能操作分发).
 */
@RestController
@RequestMapping("/api")
public class DistributionController {

    private final DistributionService service;
    private final RssFeedService rss;

    public DistributionController(DistributionService service, RssFeedService rss) {
        this.service = service;
        this.rss = rss;
    }

    // ---------- Platforms ----------

    @GetMapping("/platforms")
    public List<PlatformResponse> platforms() {
        return service.listPlatforms().stream().map(PlatformResponse::from).toList();
    }

    // ---------- Platform accounts ----------

    @GetMapping("/platform-accounts")
    public List<AccountResponse> accounts() {
        return service.listAccounts().stream()
                .map(a -> AccountResponse.from(a, service.accountPlatformName(a))).toList();
    }

    @PostMapping("/platform-accounts")
    public AccountResponse upsertAccount(@Valid @RequestBody UpsertAccountRequest req) {
        var a = service.upsertAccount(req);
        return AccountResponse.from(a, service.accountPlatformName(a));
    }

    @DeleteMapping("/platform-accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Per-episode distribution ----------

    @GetMapping("/episodes/{episodeId}/distributions")
    public List<DistributionResponse> list(@PathVariable Long episodeId) {
        return service.listForEpisode(episodeId).stream()
                .map(d -> DistributionResponse.from(d, service.distributionPlatformName(d))).toList();
    }

    @PostMapping("/episodes/{episodeId}/distributions")
    public DistributionResponse upsert(@PathVariable Long episodeId,
                                       @Valid @RequestBody UpsertDistributionRequest req) {
        var d = service.upsertDistribution(episodeId, req);
        return DistributionResponse.from(d, service.distributionPlatformName(d));
    }

    @PatchMapping("/distributions/{id}/status")
    public DistributionResponse changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody ChangeDistributionStatusRequest req) {
        var d = service.changeStatus(id, req.status());
        return DistributionResponse.from(d, service.distributionPlatformName(d));
    }

    @DeleteMapping("/distributions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDistribution(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Publish calendar ----------

    @GetMapping("/distributions/calendar")
    public List<CalendarEntry> calendar() {
        return service.calendar();
    }

    // ---------- RSS feed (README §4.4 RSS管理) ----------

    @GetMapping(value = "/podcasts/{podcastId}/rss", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> rss(@PathVariable Long podcastId, HttpServletRequest req) {
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(rss.generate(podcastId, baseUrl));
    }
}
