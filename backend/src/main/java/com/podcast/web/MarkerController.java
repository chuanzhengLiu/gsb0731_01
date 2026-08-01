package com.podcast.web;

import com.podcast.domain.MarkerStatus;
import com.podcast.domain.MarkerType;
import com.podcast.service.MarkerService;
import com.podcast.web.dto.MarkerDtos.ChangeStatusRequest;
import com.podcast.web.dto.MarkerDtos.CreateMarkerRequest;
import com.podcast.web.dto.MarkerDtos.MarkerResponse;
import com.podcast.web.dto.MarkerDtos.UpdateMarkerRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MarkerController {

    private final MarkerService service;

    public MarkerController(MarkerService service) {
        this.service = service;
    }

    /**
     * List / filter / search markers of an audio version (README §4.2).
     * All filter params optional.
     */
    @GetMapping("/audio-versions/{audioVersionId}/markers")
    public List<MarkerResponse> list(@PathVariable Long audioVersionId,
                                     @RequestParam(required = false) MarkerType type,
                                     @RequestParam(required = false) MarkerStatus status,
                                     @RequestParam(required = false) Long createdBy,
                                     @RequestParam(required = false) String keyword) {
        return service.search(audioVersionId, type, status, createdBy, keyword)
                .stream().map(MarkerResponse::from).toList();
    }

    @PostMapping("/audio-versions/{audioVersionId}/markers")
    public MarkerResponse create(@PathVariable Long audioVersionId,
                                 @Valid @RequestBody CreateMarkerRequest req) {
        return MarkerResponse.from(service.create(audioVersionId, req));
    }

    @PutMapping("/markers/{markerId}")
    public MarkerResponse update(@PathVariable Long markerId,
                                 @Valid @RequestBody UpdateMarkerRequest req) {
        return MarkerResponse.from(service.update(markerId, req));
    }

    /** Status-flow transition (README §4.2 标记状态流转). */
    @PatchMapping("/markers/{markerId}/status")
    public MarkerResponse changeStatus(@PathVariable Long markerId,
                                       @Valid @RequestBody ChangeStatusRequest req) {
        return MarkerResponse.from(service.changeStatus(markerId, req.status()));
    }

    @DeleteMapping("/markers/{markerId}")
    public ResponseEntity<Void> delete(@PathVariable Long markerId) {
        service.delete(markerId);
        return ResponseEntity.noContent().build();
    }
}
