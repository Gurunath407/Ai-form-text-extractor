package com.task.hwai.controller;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.hwai.entity.ExtractionEntity;
import com.task.hwai.model.ExtractionResult;
import com.task.hwai.model.RunResponse;
import com.task.hwai.model.RunStatus;
import com.task.hwai.service.ExtractionService;
import com.task.hwai.repo.FileRepo;
import com.task.hwai.repo.RunRepo;
import com.task.hwai.service.LangChainExtractionService;

@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class RunController {

    private final RunRepo runRepo;
    private final ExtractionService extractionService;
    private final FileRepo fileRepo;
    private final LangChainExtractionService langChainService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunController(RunRepo runRepo, ExtractionService extractionService, FileRepo fileRepo, LangChainExtractionService langChainService) {
        this.runRepo = runRepo;
        this.extractionService = extractionService;
        this.fileRepo = fileRepo;
        this.langChainService = langChainService;
    }

    // =========================
    // EXISTING APIs
    // =========================

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunResponse> getRun(@PathVariable UUID runId) throws Exception {
        var run = runRepo.findById(runId).orElse(null);
        if (run == null) return ResponseEntity.notFound().build();

        if (run.getStatus() == RunStatus.QUEUED) {
            var file = fileRepo.findById(run.getFileId()).orElseThrow();
            var result = langChainService.extractFromImage(
                    file.getFileId().toString(),
                    runId.toString(),
                    Path.of(file.getStorageUri())
            );

            ExtractionEntity extraction = extractionService.getExtractionByRunId(runId)
                    .orElseGet(ExtractionEntity::new);
            extraction.setRunId(runId);
            extraction.setDocumentType(result.documentType());
            extraction.setResultJson(objectMapper.writeValueAsString(result));
            extractionService.saveExtraction(extraction);

            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            runRepo.save(run);

            return ResponseEntity.ok(new RunResponse(RunStatus.COMPLETED, result, null));
        }

        if (run.getStatus() == RunStatus.COMPLETED) {
            var ex = extractionService.getExtractionByRunId(runId).orElse(null);
            ExtractionResult result = ex != null ? JsonUtil.read(ex.getResultJson(), ExtractionResult.class) : null;
            return ResponseEntity.ok(new RunResponse(RunStatus.COMPLETED, result, null));
        }

        if (run.getStatus() == RunStatus.FAILED) {
            return ResponseEntity.ok(new RunResponse(RunStatus.FAILED, null, run.getError()));
        }

        return ResponseEntity.ok(new RunResponse(run.getStatus(), null, null));
    }



    @PostMapping("/exports")
    public ResponseEntity<?> export(@RequestBody ExportReq req) {
        var ex = extractionService.getExtractionByRunId(req.runId()).orElse(null);
        if (ex == null) return ResponseEntity.notFound().build();
        if ("csv".equalsIgnoreCase(req.format())) {
            var csv = CsvUtil.fromExtractionJson(ex.getResultJson());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=extraction.csv")
                    .body(csv);
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=extraction.json")
                .body(ex.getResultJson());
    }

    public record ExportReq(UUID runId, String format) {}

    // =========================
    // NEW CRUD APIs for ExtractionEntity
    // =========================

    // CREATE
    @PostMapping("/extractions")
    public ResponseEntity<ExtractionEntity> createExtraction(@RequestBody ExtractionEntity extraction) {
        ExtractionEntity saved = extractionService.saveExtraction(extraction);
        return ResponseEntity.ok(saved);
    }

    // READ all
    @GetMapping("/extractions")
    public ResponseEntity<List<ExtractionEntity>> getAllExtractions() {
        return ResponseEntity.ok(extractionService.getAllExtractions());
    }

    // READ by ID
    @GetMapping("/extractions/{id}")
    public ResponseEntity<ExtractionEntity> getExtractionById(@PathVariable Long id) {
        Optional<ExtractionEntity> extraction = extractionService.getExtractionById(id);
        return extraction.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // READ by runId
    @GetMapping("/extractions/run/{runId}")
    public ResponseEntity<ExtractionEntity> getExtractionByRunId(@PathVariable UUID runId) {
        Optional<ExtractionEntity> extraction = extractionService.getExtractionByRunId(runId);
        return extraction.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // UPDATE
    @PutMapping("/extractions/{id}")
    public ResponseEntity<ExtractionEntity> updateExtraction(@PathVariable Long id, @RequestBody ExtractionEntity updated) {
        return extractionService.getExtractionById(id)
                .map(existing -> {
                    existing.setDocumentType(updated.getDocumentType());
                    existing.setResultJson(updated.getResultJson());
                    existing.setAvgConfidence(updated.getAvgConfidence());
                    existing.setRunId(updated.getRunId());
                    ExtractionEntity saved = extractionService.saveExtraction(existing);
                    return ResponseEntity.ok(saved);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // DELETE by ID
    @DeleteMapping("/extractions/{id}")
    public ResponseEntity<Void> deleteExtraction(@PathVariable Long id) {
        extractionService.deleteExtraction(id);
        System.out.println("Deleted");
        return ResponseEntity.noContent().build();
    }

    // DELETE all
    @DeleteMapping("/extractions")
    public ResponseEntity<Void> deleteAllExtractions() {
        extractionService.deleteAllExtractions();
        return ResponseEntity.noContent().build();
    }

    // =========================
    // Tiny JSON / CSV Helpers (existing)
    // =========================

    static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper M =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .setPropertyNamingStrategy(
                                com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                        .configure(
                                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                false);

        static <T> T read(String s, Class<T> t) {
            try {
                return M.readValue(s, t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class CsvUtil {
        static String fromExtractionJson(String json) {
            var M = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                var node = M.readTree(json);
                var pages = node.get("pages");
                var sb = new StringBuilder("page,field,value,confidence\n");
                if (pages != null && pages.isArray()) {
                    for (var p : pages) {
                        int page = p.path("page").asInt(1);
                        var fields = p.get("fields");
                        if (fields != null && fields.isArray()) {
                            for (var f : fields) {
                                sb.append(page).append(",")
                                        .append(escapeNode(f.get("name"))).append(",")
                                        .append(escapeNode(f.get("value"))).append(",")
                                        .append(safeText(f.get("confidence")))
                                        .append("\n");
                            }
                        }
                    }
                }
                return sb.toString();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        static String safeText(com.fasterxml.jackson.databind.JsonNode n) {
            if (n == null || n.isNull()) return "";
            return n.isValueNode() ? n.asText("") : n.toString();
        }
        static String escape(String s){ if (s==null) s=""; return "\"" + s.replace("\"","\"\"") + "\""; }
        static String escapeNode(com.fasterxml.jackson.databind.JsonNode n) { return escape(safeText(n)); }
    }
}
