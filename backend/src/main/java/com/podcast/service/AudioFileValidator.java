package com.podcast.service;

import com.podcast.config.AppProperties;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates uploaded audio (README §8): allowed extensions wav/mp3/m4a,
 * size <= 500MB, and file-header (magic byte) verification.
 */
@Component
public class AudioFileValidator {

    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;

    public AudioFileValidator(AppProperties props) {
        this.maxSizeBytes = props.getAudio().getMaxSizeBytes();
        this.allowedExtensions = Arrays.stream(props.getAudio().getAllowedExtensions().split(","))
                .map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
    }

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("上传文件为空");
        }
        if (file.getSize() > maxSizeBytes) {
            throw ApiException.badRequest("文件大小超过限制（最大 500MB）");
        }
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (ext == null || !allowedExtensions.contains(ext)) {
            throw ApiException.badRequest("不支持的音频格式，仅允许: " + allowedExtensions);
        }

        byte[] header = new byte[16];
        try {
            int read = file.getInputStream().readNBytes(header, 0, 16);
            if (read < 4) {
                throw ApiException.badRequest("文件内容无效");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("无法读取文件内容");
        }

        if (!matchesHeader(ext, header)) {
            throw ApiException.badRequest("文件头校验失败，文件可能被伪造或损坏");
        }
        return ext;
    }

    private boolean matchesHeader(String ext, byte[] h) {
        return switch (ext) {
            case "wav" -> isRiffWave(h);
            case "mp3" -> isMp3(h);
            case "m4a" -> isM4a(h);
            default -> false;
        };
    }

    // "RIFF"...."WAVE"
    private boolean isRiffWave(byte[] h) {
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E';
    }

    // ID3 tag ("ID3") or MPEG frame sync (0xFF 0xEx/0xFx)
    private boolean isMp3(byte[] h) {
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') return true;
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0;
    }

    // ISO-BMFF: bytes 4-7 == "ftyp"
    private boolean isM4a(byte[] h) {
        return h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
    }

    private String extensionOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase();
    }
}
