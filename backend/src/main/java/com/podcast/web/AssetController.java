package com.podcast.web;

import com.podcast.domain.Asset;
import com.podcast.domain.AssetType;
import com.podcast.domain.AssetUsage;
import com.podcast.domain.Episode;
import com.podcast.service.AssetService;
import com.podcast.web.dto.AssetDtos;
import com.podcast.web.dto.AssetDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Asset library endpoints (README §4.5). Library management + usage recording
 * are producer-level; reads are available to any team member.
 */
@RestController
@RequestMapping("/api")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    private AssetResponse toResponse(Asset a) {
        return AssetResponse.from(a, service.previewUrl(a));
    }

    @GetMapping("/assets")
    public List<AssetResponse> list(@RequestParam(required = false) String type) {
        AssetType t = type == null ? null : AssetDtos.parseType(type);
        return service.list(t).stream().map(this::toResponse).toList();
    }

    @PostMapping("/assets/text")
    public AssetResponse createText(@Valid @RequestBody TextAssetRequest req) {
        return toResponse(service.createText(req));
    }

    @PostMapping(value = "/assets/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse uploadAudio(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) String category,
                                     @RequestPart("file") MultipartFile file) {
        return toResponse(service.uploadAudio(name, category, file));
    }

    @PutMapping("/assets/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAssetRequest req) {
        return toResponse(service.update(id, req));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Usage tracking ----------

    @PostMapping("/assets/{id}/usages")
    public UsageResponse recordUsage(@PathVariable Long id, @Valid @RequestBody RecordUsageRequest req) {
        return toUsage(service.recordUsage(id, req));
    }

    @GetMapping("/assets/{id}/usages")
    public List<UsageResponse> usages(@PathVariable Long id) {
        return service.listUsage(id).stream().map(this::toUsage).toList();
    }

    private UsageResponse toUsage(AssetUsage u) {
        Episode e = service.episodeOf(u.getEpisodeId());
        return new UsageResponse(u.getId(), u.getAssetId(), u.getEpisodeId(),
                e != null ? e.getNumber() : null, e != null ? e.getTitle() : null,
                u.getPositionMs(), u.getNote(), u.getCreatedAt().toString());
    }
}
