package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    private final AppProperties props;
    private Path root;

    public StorageService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() throws IOException {
        if (!"local".equalsIgnoreCase(props.getStorage().getType())) {
            throw new IllegalStateException("Only local storage is implemented in this build");
        }
        root = Paths.get(props.getStorage().getLocalPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile store(InputStream inputStream, String category, String originalFilename, String contentType, long maxSize)
            throws IOException {
        String ext = extension(originalFilename, contentType);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        String relative = category + "/" + filename;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid path");
        }
        Files.createDirectories(target.getParent());
        long written = Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        if (written > maxSize) {
            Files.deleteIfExists(target);
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        if ("audio".equals(category) || "assets".equals(category)) {
            validateAudioMagicBytes(target);
        }
        return new StoredFile(relative, target, written, contentType);
    }

    private void validateAudioMagicBytes(Path file) throws IOException {
        byte[] header = new byte[12];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(header);
            if (read < 4) {
                Files.deleteIfExists(file);
                throw new ApiException(ErrorCode.BAD_FILE, "File is too small to be a valid audio file");
            }
        }
        boolean valid = isWav(header) || isMp3(header) || isM4a(header);
        if (!valid) {
            Files.deleteIfExists(file);
            throw new ApiException(ErrorCode.BAD_FILE, "File content does not match WAV/MP3/M4A audio format");
        }
    }

    private boolean isWav(byte[] h) {
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E';
    }

    private boolean isMp3(byte[] h) {
        // ID3 tag
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') return true;
        // MPEG frame sync 0xFFEx / 0xFFFx
        int b0 = h[0] & 0xFF;
        int b1 = h[1] & 0xFF;
        return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
    }

    private boolean isM4a(byte[] h) {
        // ISO BMFF: bytes 4-7 are 'ftyp'
        if (h.length < 12) return false;
        return h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
    }

    public Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Invalid path");
        }
        return resolved;
    }

    public Path requireExisting(String relativePath) {
        Path resolved = resolve(relativePath);
        if (!Files.exists(resolved)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "File not found");
        }
        return resolved;
    }

    public Path root() {
        return root;
    }

    public String toPublicUrl(String relativePath) {
        String encoded = UriUtils.encodePath(relativePath, java.nio.charset.StandardCharsets.UTF_8);
        if (relativePath.startsWith("audio/")) {
            return "/api/files/audio/" + encoded.substring("audio/".length());
        }
        if (relativePath.startsWith("peaks/")) {
            return "/api/files/peaks/" + encoded.substring("peaks/".length());
        }
        if (relativePath.startsWith("assets/")) {
            return "/api/files/assets/" + encoded.substring("assets/".length());
        }
        return "/api/files/" + encoded;
    }

    private String extension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String e = filename.substring(filename.lastIndexOf('.')).toLowerCase();
            if (e.matches("\\.[a-z0-9]{2,5}")) return e;
        }
        return switch (contentType == null ? "" : contentType) {
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/mp4", "audio/x-m4a", "audio/aac" -> ".m4a";
            case "application/json" -> ".json";
            default -> ".bin";
        };
    }

    public record StoredFile(String relativePath, Path absolutePath, long size, String contentType) {}
}
