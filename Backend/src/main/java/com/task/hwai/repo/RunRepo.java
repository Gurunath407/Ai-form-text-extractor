package com.task.hwai.repo;

import com.task.hwai.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunRepo extends JpaRepository<RunEntity, UUID> {}
