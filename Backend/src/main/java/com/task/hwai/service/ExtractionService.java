package com.task.hwai.service;

import com.task.hwai.entity.ExtractionEntity;
import com.task.hwai.repo.ExtractionRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExtractionService {

    private final ExtractionRepo extractionRepo;

    public ExtractionService(ExtractionRepo extractionRepo) {
        this.extractionRepo = extractionRepo;
    }

    // Create or Update
    public ExtractionEntity saveExtraction(ExtractionEntity extraction) {
        return extractionRepo.save(extraction);
    }

    // Read all
    public List<ExtractionEntity> getAllExtractions() {
        return extractionRepo.findAll();
    }

    // Read by ID
    public Optional<ExtractionEntity> getExtractionById(Long id) {
        return extractionRepo.findById(id);
    }

    // Read by RunId
    public Optional<ExtractionEntity> getExtractionByRunId(UUID runId) {
        return extractionRepo.findByRunId(runId);
    }

    // Delete by ID
    public void deleteExtraction(Long id) {
        extractionRepo.deleteById(id);
    }

    // Delete all
    public void deleteAllExtractions() {
        extractionRepo.deleteAll();
    }
}
