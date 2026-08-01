package com.podcast.web;

import com.podcast.service.TranscriptionService;
import com.podcast.web.dto.MarkerDtos.MarkerResponse;
import com.podcast.web.dto.TranscriptDtos.SegmentMarkerRequest;
import com.podcast.web.dto.TranscriptDtos.SegmentResponse;
import com.podcast.web.dto.TranscriptDtos.UpdateSegmentRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Transcript alignment endpoints (README §4.3). Transcripts are auto-generated
 * on upload; these endpoints read/regenerate them, edit segment text (with
 * automatic timestamp re-adjustment) and add markers from the text.
 */
@RestController
@RequestMapping("/api")
public class TranscriptController {

    private final TranscriptionService service;

    public TranscriptController(TranscriptionService service) {
        this.service = service;
    }

    @GetMapping("/audio-versions/{audioVersionId}/transcript")
    public List<SegmentResponse> list(@PathVariable Long audioVersionId) {
        return service.list(audioVersionId).stream().map(SegmentResponse::from).toList();
    }

    /** Manually (re)generate the transcript for an audio version. */
    @PostMapping("/audio-versions/{audioVersionId}/transcript")
    public List<SegmentResponse> generate(@PathVariable Long audioVersionId) {
        return service.generate(audioVersionId).stream().map(SegmentResponse::from).toList();
    }

    /** Human correction of a segment; timestamps auto-adjust (README §4.3). */
    @PutMapping("/transcript-segments/{segmentId}")
    public SegmentResponse update(@PathVariable Long segmentId,
                                  @Valid @RequestBody UpdateSegmentRequest req) {
        return SegmentResponse.from(service.updateSegment(segmentId, req));
    }

    /** Add a marker anchored to a transcript segment (README §4.3). */
    @PostMapping("/transcript-segments/{segmentId}/markers")
    public MarkerResponse addMarker(@PathVariable Long segmentId,
                                    @Valid @RequestBody SegmentMarkerRequest req) {
        return MarkerResponse.from(service.addMarkerForSegment(segmentId, req));
    }
}
