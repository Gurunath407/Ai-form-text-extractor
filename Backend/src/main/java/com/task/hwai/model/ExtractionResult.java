package com.task.hwai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ExtractionResult(
        @JsonProperty("file_id")
        String fileId,

        @JsonProperty("run_id")
        String runId,

        @JsonProperty("document_type")
        String documentType,

        @JsonProperty("pages")
        List<Page> pages,

        @JsonProperty("warnings")
        List<String> warnings,

        @JsonProperty("processing_time_ms")
        long processingTimeMs
) {

    public record Page(
            @JsonProperty("page")
            int page,

            // Single map of dynamic fields
            @JsonProperty("fields")
            Map<String, Object> fields,

            // Fully dynamic tables
            @JsonProperty("tables")
            List<Map<String, Object>> tables
    ) {}
}
