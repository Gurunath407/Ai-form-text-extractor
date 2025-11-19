package com.task.hwai.model;

import java.util.UUID;

public record UploadResponse(UUID fileId, UUID runId) {}