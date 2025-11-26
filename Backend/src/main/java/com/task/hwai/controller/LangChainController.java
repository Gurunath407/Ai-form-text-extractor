package com.task.hwai.controller;

import com.task.hwai.model.ExtractionResult;
import com.task.hwai.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/langchain")
@CrossOrigin(origins = "*")
public class LangChainController {

    private final LangChainExtractionService extractionService;
    private final LangChainAgentService agentService;
    private final StructuredExtractionParser parser;

    public LangChainController(
            LangChainExtractionService extractionService,
            LangChainAgentService agentService,
            StructuredExtractionParser parser
    ) {
        this.extractionService = extractionService;
        this.agentService = agentService;
        this.parser = parser;
    }

    @PostMapping("/extract/simple")
    public ResponseEntity<?> simpleExtraction(
            @RequestParam UUID fileId,
            @RequestParam UUID runId,
            @RequestParam String imagePath
    ) {
        try {
            ExtractionResult result = extractionService.extractFromImage(
                    fileId.toString(),
                    runId.toString(),
                    Path.of(imagePath)
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "method", "langchain_simple"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/extract/agent")
    public ResponseEntity<?> agentExtraction(
            @RequestParam UUID fileId,
            @RequestParam UUID runId,
            @RequestParam String imagePath
    ) {
        try {
            ExtractionResult result = agentService.processWithAgent(
                    fileId.toString(),
                    runId.toString(),
                    Path.of(imagePath)
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "method", "langchain_agent_with_tools"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/extract/structured")
    public ResponseEntity<?> structuredExtraction(
            @RequestParam UUID fileId,
            @RequestParam UUID runId,
            @RequestBody Map<String, String> request
    ) {
        try {
            String ocrText = request.get("ocr_text");
            if (ocrText == null || ocrText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "ocr_text is required"
                ));
            }

            ExtractionResult result = parser.parseStructuredExtraction(
                    ocrText,
                    fileId.toString(),
                    runId.toString()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "method", "structured_parser_with_confidence"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/parse/fields")
    public ResponseEntity<?> parseFieldsWithConfidence(
            @RequestBody Map<String, String> request
    ) {
        try {
            String ocrText = request.get("ocr_text");
            if (ocrText == null || ocrText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "ocr_text is required"
                ));
            }

            Map<String, Object> result = parser.parseFieldsWithConfidence(ocrText);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/parse/tables")
    public ResponseEntity<?> parseTablesFromOcr(
            @RequestBody Map<String, String> request
    ) {
        try {
            String ocrText = request.get("ocr_text");
            if (ocrText == null || ocrText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "ocr_text is required"
                ));
            }

            var tables = parser.parseTablesFromOcr(ocrText);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tables", tables
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "langchain_version", "1.8.0",
                "features", new String[]{
                        "simple_extraction",
                        "agent_with_tools",
                        "structured_parsing",
                        "field_confidence_scoring",
                        "table_detection"
                }
        ));
    }
}
