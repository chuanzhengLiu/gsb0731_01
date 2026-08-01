package com.podcast.collab.web;

import com.podcast.collab.domain.AudioVersion;
import com.podcast.collab.repo.AudioVersionRepository;
import com.podcast.collab.service.SignedUrlService;
import com.podcast.collab.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class FileStreamController {

    private final StorageService storage;
    private final SignedUrlService signedUrlService;
    private final AudioVersionRepository versionRepo;

    public FileStreamController(StorageService storage, SignedUrlService signedUrlService,
                                AudioVersionRepository versionRepo) {
        this.storage = storage;
        this.signedUrlService = signedUrlService;
        this.versionRepo = versionRepo;
    }

    @GetMapping("/files/audio/{filename:.+}")
    public ResponseEntity<Resource> streamAudio(
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String range,
            jakarta.servlet.http.HttpServletRequest request) throws IOException {
        SignedUrlService.Verification verification = signedUrlService.verify(request);
        String relative = "audio/" + filename;
        Path file = storage.requireExisting(relative);

        // Archived versions are download-only: never streamed/played online.
        AudioVersion version = versionRepo.findFirstByFileUrl(request.getRequestURI()).orElse(null);
        if (version != null && version.isArchived() && !verification.download()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ContentDisposition disposition = ContentDisposition.builder(verification.download() ? "attachment" : "inline")
                .filename(version != null ? version.getFileName() : filename)
                .build();
        return serve(file, range, true, disposition);
    }

    @GetMapping("/files/peaks/{filename:.+}")
    public ResponseEntity<Resource> peaks(@PathVariable String filename,
                                          jakarta.servlet.http.HttpServletRequest request) throws IOException {
        signedUrlService.verify(request);
        return serve(storage.requireExisting("peaks/" + filename), null, false,
                ContentDisposition.inline().build());
    }

    @GetMapping("/files/assets/{filename:.+}")
    public ResponseEntity<Resource> assets(@PathVariable String filename,
                                           jakarta.servlet.http.HttpServletRequest request) throws IOException {
        signedUrlService.verify(request);
        return serve(storage.requireExisting("assets/" + filename), null, false,
                ContentDisposition.inline().build());
    }

    private ResponseEntity<Resource> serve(Path file, String range, boolean acceptRanges,
                                           ContentDisposition disposition) throws IOException {
        long length = Files.size(file);
        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(disposition);

        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring("bytes=".length()).split("-");
            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isBlank()
                    ? Long.parseLong(parts[1]) : length - 1;
            if (start >= length || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length).build();
            }
            if (end >= length) end = length - 1;
            long chunk = end - start + 1;
            headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
            headers.setContentLength(chunk);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            InputStream in = Files.newInputStream(file);
            skipFully(in, start);
            InputStream bounded = new BoundedInputStream(in, chunk);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers)
                    .body(new InputStreamResource(bounded));
        }

        headers.setContentLength(length);
        if (acceptRanges) headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(Files.newInputStream(file)));
    }

    private void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) break;
            remaining -= skipped;
        }
    }

    private static class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = in.read();
            if (b != -1) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = in.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
