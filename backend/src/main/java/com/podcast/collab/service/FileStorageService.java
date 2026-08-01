package com.podcast.collab.service;

import com.podcast.collab.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageLocation;
    private final Path audioLocation;
    private final Path waveformLocation;

    public FileStorageService(
            @Value("${app.storage.location:./storage}") String storageLocation,
            @Value("${app.storage.audio-subdir:audio}") String audioSubdir,
            @Value("${app.storage.waveform-subdir:waveforms}") String waveformSubdir) {
        this.storageLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.audioLocation = this.storageLocation.resolve(audioSubdir);
        this.waveformLocation = this.storageLocation.resolve(waveformSubdir);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(audioLocation);
            Files.createDirectories(waveformLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directories", e);
        }
    }

    public String storeAudioFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("File name is required");
        }
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String filename = UUID.randomUUID() + extension;
        try {
            Path target = audioLocation.resolve(filename).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store audio file", e);
        }
    }

    public String storeWaveformData(String json, Long versionId) {
        String filename = "waveform_" + versionId + ".json";
        try {
            Path target = waveformLocation.resolve(filename).normalize();
            Files.writeString(target, json, StandardCharsets.UTF_8);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store waveform data", e);
        }
    }

    public Resource loadAudioAsResource(String filename) {
        Path path = audioLocation.resolve(filename).normalize();
        Resource resource = new FileSystemResource(path);
        if (!resource.exists() || !resource.isReadable()) {
            throw new com.podcast.collab.exception.ResourceNotFoundException("Audio file not found: " + filename);
        }
        return resource;
    }

    public Resource loadWaveformAsResource(String filename) {
        Path path = waveformLocation.resolve(filename).normalize();
        Resource resource = new FileSystemResource(path);
        if (!resource.exists() || !resource.isReadable()) {
            throw new com.podcast.collab.exception.ResourceNotFoundException("Waveform file not found: " + filename);
        }
        return resource;
    }

    public String getWaveformContent(String filename) {
        try {
            Path path = waveformLocation.resolve(filename).normalize();
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read waveform file", e);
        }
    }

    public void deleteAudioFile(String filename) {
        try {
            Path path = audioLocation.resolve(filename).normalize();
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete audio file", e);
        }
    }

    public Path getAudioPath(String filename) {
        return audioLocation.resolve(filename).normalize();
    }
}
