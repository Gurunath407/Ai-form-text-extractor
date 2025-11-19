package com.task.hwai.model;

public record RunResponse(
        RunStatus status,
        ExtractionResult result,
        String error
) {}