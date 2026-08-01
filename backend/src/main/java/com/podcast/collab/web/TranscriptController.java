package com.podcast.collab.web;

import com.podcast.collab.dto.TranscriptDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TranscriptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audio-versions/{versionId}/transcript")
public class TranscriptController {

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @GetMapping
    public List<SegmentResponse> list(@PathVariable Long versionId) {
        return transcriptService.list(versionId, SecurityUtils.requireUser());
    }

    @PutMapping
    public List<SegmentResponse> save(@PathVariable Long versionId, @Valid @RequestBody SaveTranscriptRequest req) {
        return transcriptService.save(versionId, req, SecurityUtils.requireUser());
    }
}
