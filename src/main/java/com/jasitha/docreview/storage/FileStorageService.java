package com.jasitha.docreview.storage;

import com.jasitha.docreview.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores uploaded files on local disk under random names, so a user-supplied
 * filename can never be used for path traversal. Swap this for S3 later.
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(AppProperties properties) throws IOException {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String store(MultipartFile file) throws IOException {
        Path target = root.resolve(UUID.randomUUID().toString()).normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString();
    }

    public Path load(String storagePath) {
        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside the upload directory");
        }
        return path;
    }
}
