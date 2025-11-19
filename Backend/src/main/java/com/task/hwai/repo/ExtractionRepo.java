package com.task.hwai.repo;

import com.task.hwai.entity.ExtractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExtractionRepo extends JpaRepository<ExtractionEntity, Long> {
    Optional<ExtractionEntity> findByRunId(UUID runId);
}
