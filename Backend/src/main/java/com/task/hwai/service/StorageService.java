package com.task.hwai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;

@Service
public class StorageService {
    private final Path baseDir;

    public StorageService(@Value("${app.storage.localDir}") String dir) {
        this.baseDir = Path.of(dir);
        try { Files.createDirectories(baseDir); } catch (Exception ignored) {}
    }

    public Path save(MultipartFile file, String fileId) throws Exception {
        Path p = baseDir.resolve(fileId + "_" + file.getOriginalFilename());
        Files.copy(file.getInputStream(), p, StandardCopyOption.REPLACE_EXISTING);
        return p;
    }
}
