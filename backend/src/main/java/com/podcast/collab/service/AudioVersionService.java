package com.podcast.collab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.dto.audio.AudioVersionResponse;
import com.podcast.collab.dto.audio.UploadAudioResponse;
import com.podcast.collab.dto.audio.WaveformResponse;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.User;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AudioVersionService {

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("wav", "mp3", "m4a"));
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    private final AudioVersionRepository audioVersionRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AudioProcessor audioProcessor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AudioVersionService self;
    private final int maxKeep;

    public AudioVersionService(AudioVersionRepository audioVersionRepository,
                               EpisodeRepository episodeRepository,
                               PodcastRepository podcastRepository,
                               TeamMemberRepository teamMemberRepository,
                               UserRepository userRepository,
                               FileStorageService fileStorageService,
                               AudioProcessor audioProcessor,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               @Lazy AudioVersionService self,
                               @Value("${app.version.max-keep:10}") int maxKeep) {
        this.audioVersionRepository = audioVersionRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.audioProcessor = audioProcessor;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.self = self;
        this.maxKeep = maxKeep;
    }

    @Transactional
    public UploadAudioResponse uploadAudio(Long episodeId, MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Audio file is required");
        }

        validateAudioFile(file);

        Episode episode = findEpisodeOrThrow(episodeId);
        verifyEpisodeTeamAccess(episode, userId);

        int versionNumber = audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio";
        String storedFilename = fileStorageService.storeAudioFile(file);
        Path audioPath = fileStorageService.getAudioPath(storedFilename);
        long fileSize;
        try {
            fileSize = Files.size(audioPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file size", e);
        }
        long durationMs = audioProcessor.extractDurationMs(audioPath);
        String waveformJson = audioProcessor.generateWaveformJson(audioPath);

        AudioVersion version = AudioVersion.builder()
                .episodeId(episodeId)
                .versionNumber(versionNumber)
                .fileUrl(storedFilename)
                .fileName(originalFilename)
                .fileSize(fileSize)
                .durationMs(durationMs)
                .uploadedBy(userId)
                .isArchived(false)
                .build();
        version = audioVersionRepository.save(version);

        String waveformFilename = fileStorageService.storeWaveformData(waveformJson, version.getId());
        version.setWaveformUrl(waveformFilename);
        version = audioVersionRepository.save(version);

        long count = audioVersionRepository.countByEpisodeId(episodeId);
        if (count > maxKeep) {
            self.archiveOldVersions(episodeId);
        }

        auditService.log(userId, "UPLOAD_AUDIO", "AudioVersion", version.getId(),
                "Audio version " + versionNumber + " uploaded for episode " + episodeId);

        return UploadAudioResponse.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .durationMs(version.getDurationMs())
                .waveformUrl(version.getWaveformUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public AudioVersionResponse getVersion(Long versionId, Long userId) {
        AudioVersion version = verifyVersionAccess(versionId, userId);
        return mapToResponse(version);
    }

    @Transactional(readOnly = true)
    public List<AudioVersionResponse> listVersions(Long episodeId, Long userId) {
        Episode episode = findEpisodeOrThrow(episodeId);
        verifyEpisodeTeamAccess(episode, userId);
        return audioVersionRepository.findByEpisodeIdOrderByVersionNumberDesc(episodeId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AudioVersionResponse getLatestVersion(Long episodeId, Long userId) {
        Episode episode = findEpisodeOrThrow(episodeId);
        verifyEpisodeTeamAccess(episode, userId);
        AudioVersion version = audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("No audio version found for this episode"));
        return mapToResponse(version);
    }

    @Transactional(readOnly = true)
    public WaveformResponse getWaveform(Long versionId, Long userId) {
        AudioVersion version = verifyVersionAccess(versionId, userId);
        if (version.getWaveformUrl() == null) {
            throw new ResourceNotFoundException("Waveform not found for this version");
        }
        String json = fileStorageService.getWaveformContent(version.getWaveformUrl());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode samplesNode = root.path("samples");
            List<Double> samples = new ArrayList<>();
            if (samplesNode.isArray()) {
                for (JsonNode node : samplesNode) {
                    samples.add(node.asDouble());
                }
            }
            return WaveformResponse.builder()
                    .versionId(version.getId())
                    .durationMs(version.getDurationMs())
                    .samples(samples)
                    .sampleCount(samples.size())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse waveform data", e);
        }
    }

    @Transactional(readOnly = true)
    public void streamAudio(Long versionId, HttpServletRequest request, HttpServletResponse response, Long userId) {
        AudioVersion version = verifyVersionAccess(versionId, userId);
        streamAudioInternal(version, request, response);
    }

    @Transactional(readOnly = true)
    public void streamAudioForShare(Long versionId, HttpServletRequest request, HttpServletResponse response) {
        AudioVersion version = findVersionOrThrow(versionId);
        streamAudioInternal(version, request, response);
    }

    @Transactional(readOnly = true)
    public WaveformResponse getWaveformForShare(Long versionId) {
        AudioVersion version = findVersionOrThrow(versionId);
        if (version.getWaveformUrl() == null) {
            throw new ResourceNotFoundException("Waveform not found for this version");
        }
        String json = fileStorageService.getWaveformContent(version.getWaveformUrl());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode samplesNode = root.path("samples");
            List<Double> samples = new ArrayList<>();
            if (samplesNode.isArray()) {
                for (JsonNode node : samplesNode) {
                    samples.add(node.asDouble());
                }
            }
            return WaveformResponse.builder()
                    .versionId(version.getId())
                    .durationMs(version.getDurationMs())
                    .samples(samples)
                    .sampleCount(samples.size())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse waveform data", e);
        }
    }

    private void streamAudioInternal(AudioVersion version, HttpServletRequest request, HttpServletResponse response) {
        Path audioPath = fileStorageService.getAudioPath(version.getFileUrl());
        if (!Files.exists(audioPath)) {
            throw new ResourceNotFoundException("Audio file not found");
        }

        long fileLength;
        try {
            fileLength = Files.size(audioPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file", e);
        }

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        String contentType = resolveContentType(version.getFileName());

        if (rangeHeader == null || rangeHeader.isBlank()) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(contentType);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setContentLengthLong(fileLength);
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(audioPath, out);
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream audio", e);
            }
            return;
        }

        long[] range = parseRange(rangeHeader, fileLength);
        long start = range[0];
        long end = range[1];
        long contentLength = end - start + 1;

        response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
        response.setContentLengthLong(contentLength);

        try (RandomAccessFile raf = new RandomAccessFile(audioPath.toFile(), "r");
             OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remaining = contentLength;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to stream audio range", e);
        }
    }

    @Transactional
    public AudioVersionResponse setEpisodeFinalAudio(Long episodeId, Long versionId, Long userId) {
        Episode episode = findEpisodeOrThrow(episodeId);
        verifyEpisodeTeamAccess(episode, userId);
        AudioVersion version = findVersionOrThrow(versionId);
        if (!version.getEpisodeId().equals(episodeId)) {
            throw new BadRequestException("Audio version does not belong to this episode");
        }
        episode.setFinalAudioUrl(version.getFileUrl());
        episodeRepository.save(episode);

        auditService.log(userId, "SET_FINAL_AUDIO", "Episode", episodeId,
                "Final audio set to version " + version.getVersionNumber());

        return mapToResponse(version);
    }

    @Async
    @Transactional
    public void archiveOldVersions(Long episodeId) {
        List<AudioVersion> versions = audioVersionRepository.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        if (versions.size() <= maxKeep) {
            return;
        }
        for (int i = maxKeep; i < versions.size(); i++) {
            AudioVersion version = versions.get(i);
            if (!Boolean.TRUE.equals(version.getIsArchived())) {
                version.setIsArchived(true);
                audioVersionRepository.save(version);
            }
        }
    }

    private void validateAudioFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("File name is required");
        }

        long size = file.getSize();
        if (size > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum allowed size of 500MB");
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex + 1).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported audio format. Only WAV, MP3 and M4A files are allowed");
        }

        try {
            byte[] header = new byte[12];
            int read = file.getInputStream().read(header);
            if (read < 4) {
                throw new BadRequestException("File is too small or corrupted");
            }

            boolean validMagic = false;
            String detectedType = detectAudioTypeByMagicBytes(header);

            if ("wav".equals(detectedType) && "wav".equals(extension)) {
                validMagic = true;
            } else if ("mp3".equals(detectedType) && "mp3".equals(extension)) {
                validMagic = true;
            } else if ("m4a".equals(detectedType) && "m4a".equals(extension)) {
                validMagic = true;
            }

            if (!validMagic) {
                throw new BadRequestException(
                    "File content does not match its extension. The file header does not appear to be a valid "
                    + extension.toUpperCase() + " file. Please ensure you are uploading a genuine audio file.");
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file header: " + e.getMessage());
        }
    }

    private String detectAudioTypeByMagicBytes(byte[] header) {
        if (header.length >= 12) {
            String riff = new String(header, 0, 4);
            String wave = new String(header, 8, 4);
            if ("RIFF".equals(riff) && "WAVE".equals(wave)) {
                return "wav";
            }
        }

        if (header.length >= 3) {
            String id3 = new String(header, 0, 3);
            if ("ID3".equals(id3)) {
                return "mp3";
            }
            if ((header[0] & 0xFF) == 0xFF && ((header[1] & 0xE0) == 0xE0)) {
                return "mp3";
            }
        }

        if (header.length >= 8) {
            String ftyp = new String(header, 4, 4);
            if ("ftyp".equals(ftyp)) {
                return "m4a";
            }
        }

        return null;
    }

    private AudioVersion verifyVersionAccess(Long versionId, Long userId) {
        AudioVersion version = findVersionOrThrow(versionId);
        Episode episode = findEpisodeOrThrow(version.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);
        return version;
    }

    private void verifyEpisodeTeamAccess(Episode episode, Long userId) {
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);
    }

    private AudioVersion findVersionOrThrow(Long versionId) {
        return audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Audio version not found"));
    }

    private Episode findEpisodeOrThrow(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyTeamMembership(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".wav")) {
            return "audio/wav";
        } else if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (lower.endsWith(".aac")) {
            return "audio/aac";
        } else if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private long[] parseRange(String rangeHeader, long fileLength) {
        String bytesPart = rangeHeader.substring("bytes=".length()).trim();
        String[] parts = bytesPart.split("-");
        long start;
        long end;
        if (parts.length == 2) {
            if (parts[0].isBlank()) {
                long suffix = Long.parseLong(parts[1]);
                start = Math.max(0, fileLength - suffix);
                end = fileLength - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts[1].isBlank() ? fileLength - 1 : Long.parseLong(parts[1]);
            }
        } else {
            start = Long.parseLong(parts[0]);
            end = fileLength - 1;
        }
        if (start < 0 || start >= fileLength || end >= fileLength || start > end) {
            throw new BadRequestException("Invalid byte range: " + rangeHeader);
        }
        return new long[]{start, end};
    }

    private AudioVersionResponse mapToResponse(AudioVersion version) {
        String uploadedByName = userRepository.findById(version.getUploadedBy())
                .map(User::getName)
                .orElse(null);
        return AudioVersionResponse.builder()
                .id(version.getId())
                .episodeId(version.getEpisodeId())
                .versionNumber(version.getVersionNumber())
                .fileUrl(version.getFileUrl())
                .fileName(version.getFileName())
                .fileSize(version.getFileSize())
                .durationMs(version.getDurationMs())
                .waveformUrl(version.getWaveformUrl())
                .uploadedBy(version.getUploadedBy())
                .uploadedByName(uploadedByName)
                .isArchived(version.getIsArchived())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
