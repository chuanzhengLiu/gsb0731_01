package com.podcast.web;

import com.podcast.domain.AudioVersion;
import com.podcast.domain.Asset;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.service.AssetService;
import com.podcast.service.SignedUrlService;
import com.podcast.service.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streams audio protected by a short-lived HMAC-signed token (README §8).
 * Supports HTTP Range requests for smooth large-file streaming (README §9).
 * Archived versions are downloadable but not streamed online (README §9).
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final SignedUrlService signedUrl;
    private final AudioVersionRepository repo;
    private final StorageService storage;
    private final AssetService assetService;

    public MediaController(SignedUrlService signedUrl, AudioVersionRepository repo,
                           StorageService storage, AssetService assetService) {
        this.signedUrl = signedUrl;
        this.repo = repo;
        this.storage = storage;
        this.assetService = assetService;
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<ResourceRegion> stream(@PathVariable Long id,
                                                 @RequestParam("token") String token,
                                                 @RequestHeader HttpHeaders headers) throws Exception {
        Long verifiedId = signedUrl.verify(token);
        if (!verifiedId.equals(id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AudioVersion v = repo.findById(id).orElse(null);
        if (v == null) {
            return ResponseEntity.notFound().build();
        }
        if (v.isArchived()) {
            // Archived versions may be downloaded, not streamed online.
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        Path path = storage.resolve(v.getFileKey());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        long contentLength = resource.contentLength();
        MediaType mediaType = MediaType.parseMediaType(
                v.getContentType() != null ? v.getContentType() : "application/octet-stream");

        ResourceRegion region = resourceRegion(resource, headers, contentLength);
        HttpStatus status = headers.getRange().isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        return ResponseEntity.status(status)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private ResourceRegion resourceRegion(Resource resource, HttpHeaders headers, long contentLength)
            throws Exception {
        long chunkSize = 1024 * 1024; // 1MB per range chunk
        if (!headers.getRange().isEmpty()) {
            HttpRange range = headers.getRange().get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(chunkSize, end - start + 1);
            return new ResourceRegion(resource, start, rangeLength);
        }
        long rangeLength = Math.min(chunkSize, contentLength);
        return new ResourceRegion(resource, 0, rangeLength);
    }

    /** Download endpoint (works for archived versions too). */
    @GetMapping("/versions/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam("token") String token) throws Exception {
        Long verifiedId = signedUrl.verify(token);
        if (!verifiedId.equals(id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AudioVersion v = repo.findById(id).orElse(null);
        if (v == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = storage.resolve(v.getFileKey());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + (v.getOriginalName() != null ? v.getOriginalName() : "audio") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Asset preview streaming (README §4.5 支持试听). Protected by a signed
     * asset token; supports Range requests like audio-version streaming.
     */
    @GetMapping("/assets/{id}")
    public ResponseEntity<ResourceRegion> assetPreview(@PathVariable Long id,
                                                       @RequestParam("token") String token,
                                                       @RequestHeader HttpHeaders headers) throws Exception {
        Long verifiedId = signedUrl.verify("asset", token);
        if (!verifiedId.equals(id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Asset a = assetService.getForStreaming(id);
        if (a.getFileKey() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = storage.resolve(a.getFileKey());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        long contentLength = resource.contentLength();
        MediaType mediaType = MediaType.parseMediaType(
                a.getContentType() != null ? a.getContentType() : "application/octet-stream");
        ResourceRegion region = resourceRegion(resource, headers, contentLength);
        HttpStatus status = headers.getRange().isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        return ResponseEntity.status(status)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }
}
