package com.task.hwai.repo;

import com.task.hwai.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileRepo extends JpaRepository<FileEntity, UUID> {}