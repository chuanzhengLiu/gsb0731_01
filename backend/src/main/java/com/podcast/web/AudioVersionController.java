package com.podcast.web;

import com.podcast.domain.AudioVersion;
import com.podcast.service.AudioVersionService;
import com.podcast.web.dto.AudioVersionDtos.AudioVersionResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AudioVersionController {

    private final AudioVersionService service;

    public AudioVersionController(AudioVersionService service) {
        this.service = service;
    }

    @GetMapping("/episodes/{episodeId}/audio-versions")
    public List<AudioVersionResponse> list(@PathVariable Long episodeId) {
        return service.list(episodeId).stream()
                .map(v -> AudioVersionResponse.from(v, service.streamUrlFor(v)))
                .toList();
    }

    /** Upload a new audio version. Producers/editors/admins may upload. */
    @PostMapping(value = "/episodes/{episodeId}/audio-versions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCER','EDITOR')")
    public AudioVersionResponse upload(@PathVariable Long episodeId,
                                       @RequestPart("file") MultipartFile file) {
        AudioVersion v = service.upload(episodeId, file);
        return AudioVersionResponse.from(v, service.streamUrlFor(v));
    }

    @GetMapping("/audio-versions/{id}")
    public AudioVersionResponse get(@PathVariable Long id) {
        AudioVersion v = service.get(id);
        return AudioVersionResponse.from(v, service.streamUrlFor(v));
    }

    /** Pre-generated waveform peaks JSON for wavesurfer.js. */
    @GetMapping(value = "/audio-versions/{id}/waveform", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> waveform(@PathVariable Long id) {
        AudioVersion v = service.get(id);
        if (v.getWaveformJson() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(v.getWaveformJson());
    }

    /**
     * Compare two audio versions of an episode (README §4.2 版本对比):
     * returns both durations and the delta in ms.
     */
    @GetMapping("/audio-versions/compare")
    public AudioVersionService.VersionComparison compare(@RequestParam Long from,
                                                         @RequestParam Long to) {
        return service.compare(from, to);
    }
}
