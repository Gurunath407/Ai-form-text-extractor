package com.task.hwai.controller;

import com.task.hwai.entity.FileEntity;
import com.task.hwai.entity.RunEntity;
import com.task.hwai.model.*;
import com.task.hwai.repo.FileRepo;
import com.task.hwai.repo.RunRepo;
import com.task.hwai.service.StorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class UploadController {

    private final StorageService storage;
    private final FileRepo fileRepo;
    private final RunRepo runRepo;

    public UploadController(StorageService storage, FileRepo fileRepo, RunRepo runRepo) {
        this.storage = storage; this.fileRepo = fileRepo; this.runRepo = runRepo;
    }

    @PostMapping(value="/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestPart("file") MultipartFile file) throws Exception {
        var fe = new FileEntity();
        fe.setName(file.getOriginalFilename());
        fe.setMimeType(file.getContentType());
        fe.setSize(file.getSize());

        UUID fileId = fe.getFileId();
        Path saved = storage.save(file, fileId.toString());
        fe.setStorageUri(saved.toString());
        fileRepo.save(fe);

        var run = new RunEntity();
        run.setFileId(fileId);
        run.setStatus(RunStatus.QUEUED);
        runRepo.save(run);

        // For MVP we run inline; in prod enqueue to SQS/worker
        return new UploadResponse(fileId, run.getRunId());
    }
}
