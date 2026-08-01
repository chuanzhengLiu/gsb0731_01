package com.podcast.service;

import com.podcast.config.AppProperties;
import com.podcast.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/** Local filesystem storage (dev). Production maps to MinIO (README §5). */
@Service
public class StorageService {

    private final Path root;

    public StorageService(AppProperties props) {
        this.root = Paths.get(props.getStorage().getRoot()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录: " + root, e);
        }
    }

    /** Stores the given input under {episodeId}/{key} and returns the relative key. */
    public String store(Long episodeId, String fileName, InputStream in) {
        try {
            Path dir = root.resolve("episodes").resolve(String.valueOf(episodeId));
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(root)) {
                throw ApiException.badRequest("非法文件路径");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return root.relativize(target).toString();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "文件存储失败: " + e.getMessage());
        }
    }

    /** Stores an asset file under {@code assets/{teamId}/{fileName}}. */
    public String storeAsset(Long teamId, String fileName, InputStream in) {
        try {
            Path dir = root.resolve("assets").resolve(String.valueOf(teamId));
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(root)) {
                throw ApiException.badRequest("非法文件路径");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return root.relativize(target).toString();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "文件存储失败: " + e.getMessage());
        }
    }

    public Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.badRequest("非法文件路径");
        }
        return p;
    }

    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    public long size(String key) {
        try {
            return Files.size(resolve(key));
        } catch (IOException e) {
            return -1;
        }
    }
}
