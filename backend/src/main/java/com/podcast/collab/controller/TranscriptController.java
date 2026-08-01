package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.transcript.BulkTranscriptRequest;
import com.podcast.collab.dto.transcript.TranscriptSegmentResponse;
import com.podcast.collab.dto.transcript.UpdateTranscriptSegmentRequest;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TranscriptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transcripts")
public class TranscriptController {

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @GetMapping("/version/{versionId}")
    public ApiResponse<List<TranscriptSegmentResponse>> getSegments(@PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TranscriptSegmentResponse> response = transcriptService.getSegments(versionId, userId);
        return ApiResponse.ok(response);
    }

    @PostMapping("/version/{versionId}/bulk")
    public ApiResponse<List<TranscriptSegmentResponse>> bulkUpload(
            @PathVariable Long versionId,
            @Valid @RequestBody BulkTranscriptRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TranscriptSegmentResponse> response = transcriptService.bulkUpload(versionId, request, userId);
        return ApiResponse.ok("Transcript segments uploaded successfully", response);
    }

    @PutMapping("/segment/{segmentId}")
    public ApiResponse<TranscriptSegmentResponse> updateSegment(
            @PathVariable Long segmentId,
            @Valid @RequestBody UpdateTranscriptSegmentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TranscriptSegmentResponse response = transcriptService.updateSegment(segmentId, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/segment/{segmentId}")
    public ApiResponse<Void> deleteSegment(@PathVariable Long segmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        transcriptService.deleteSegment(segmentId, userId);
        return ApiResponse.ok("Transcript segment deleted successfully", null);
    }

    @PostMapping("/version/{versionId}/generate")
    public ApiResponse<List<TranscriptSegmentResponse>> generateTranscript(@PathVariable Long versionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TranscriptSegmentResponse> response = transcriptService.generateTranscript(versionId, userId);
        return ApiResponse.ok(response);
    }
}
