package com.task.hwai.service;

import com.task.hwai.entity.ExtractionEntity;
import com.task.hwai.model.ExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.task.hwai.model.RunStatus;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.OpenAiHttpException;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Service
public class AgentService {

    private final OpenAiService llm;
    private final OcrTool ocr;
    private final ObjectMapper om = new ObjectMapper();
    private final Tracer tracer;
    private final com.task.hwai.repo.RunRepo runRepo;
    private final com.task.hwai.repo.ExtractionRepo extractionRepo;
    private final String openaiModel;

    public AgentService(
            OpenAiService llm,
            OcrTool ocr,
            Tracer tracer,
            com.task.hwai.repo.RunRepo runRepo,
            com.task.hwai.repo.ExtractionRepo extractionRepo,
            @Value("${openai.model}") String openaiModel
    ) {
        this.llm = llm;
        this.ocr = ocr;
        this.tracer = tracer;
        this.runRepo = runRepo;
        this.extractionRepo = extractionRepo;
        this.openaiModel = openaiModel;
    }

    @Transactional
    public ExtractionResult run(UUID fileId, UUID runId, Path path) throws Exception {

        var run = runRepo.findById(runId).orElseThrow();
        run.setStatus(RunStatus.PROCESSING);
        run.setStartedAt(Instant.now());
        runRepo.save(run);

        long t0 = System.currentTimeMillis();

        Span root = tracer.spanBuilder("agent.run")
                .setAttribute("file.id", fileId.toString())
                .setAttribute("run.id", runId.toString())
                .startSpan();

        try {
            // ------------------ OCR ------------------
            var ocrSpan = tracer.spanBuilder("ocr.extract").startSpan();
            List<OcrTool.PageResult> pages;
            try {
                pages = ocr.extract(path);
            } finally {
                ocrSpan.end();
            }

            Map<String, Object> payload = Map.of("pages", pages);

            // ------------------ PROMPT ------------------
            String systemPrompt = """
                You are a strict information extraction engine.
                Convert OCR text into structured JSON ONLY.
                

                RULES:
                - Output ONLY VALID JSON.
                - No explanations.
                - Follow EXACT schema:
                

                {
                  "file_id": string,
                  "run_id": string,
                  "document_type": string,
                  "pages": [ {
                    "page": number,
                    "fields": {any dynamic keys},
                    "tables": [ {any dynamic keys} ]
                  } ],
                  "warnings": [string],
                  "processing_time_ms": number
                }
            """;

            String userPayload = om.writeValueAsString(payload);

            // ------------------ LLM CALL (with 429 retry) ------------------
            var llmSpan = tracer.spanBuilder("llm.structuring").startSpan();
            String rawOutput = null;

            try {
                List<ChatMessage> messages = List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", "OCR_DATA:\n" + userPayload)
                );
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(openaiModel)
                        .messages(messages)
                        .maxTokens(3000)
                        .temperature(0.0)
                        .build();

                rawOutput = runWith429Retry(() -> {
                    var response = llm.createChatCompletion(request);
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        ChatCompletionChoice choice = response.getChoices().get(0);
                        return choice.getMessage().getContent();
                    }
                    return null;
                });

                System.out.println("LLM RAW OUTPUT --> " + rawOutput);

            } catch (Exception ex) {
                System.err.println("❌ LLM call failed: " + ex.getMessage());
                ex.printStackTrace();
                rawOutput = null;
            } finally {
                llmSpan.end();
            }

            if (rawOutput == null || rawOutput.isBlank()) {
                return saveFallback(fileId, runId, t0, "LLM returned null or empty response");
            }

            // ------------------ CLEAN JSON ------------------
            String json = cleanJsonResponse(rawOutput);
            System.out.println("CLEANED JSON --> " + json);

            // ------------------ PARSE JSON ------------------
            ExtractionResult result;
            try {
                result = om.readValue(json, ExtractionResult.class);
            } catch (Exception e) {
                System.err.println("❌ INVALID JSON FROM LLM");
                e.printStackTrace();
                return saveFallback(fileId, runId, t0, "LLM returned invalid JSON");
            }

            // ------------------ ENFORCE IDs ------------------
            result = new ExtractionResult(
                    fileId.toString(),
                    runId.toString(),
                    result.documentType() == null ? "generic_form" : result.documentType(),
                    result.pages(),
                    result.warnings(),
                    System.currentTimeMillis() - t0
            );

            // ------------------ SAVE SUCCESS ------------------
            ExtractionEntity ex = new ExtractionEntity();
            ex.setRunId(runId);
            ex.setDocumentType(result.documentType());
            ex.setResultJson(om.writeValueAsString(result));
            ex.setAvgConfidence(averageConfidence(result));

            extractionRepo.save(ex);

            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            runRepo.save(run);

            return result;

        } catch (Exception ex) {
            run.setStatus(RunStatus.FAILED);
            run.setCompletedAt(Instant.now());
            run.setError(ex.getMessage());
            runRepo.save(run);
            throw ex;
        } finally {
            root.end();
        }
    }

    // ------------------ HELPERS ------------------

    private <T> T runWith429Retry(SupplierWithException<T> task) throws Exception {
        int maxAttempts = 5;
        long[] backoffsMs = {1200, 2000, 4000, 8000, 16000};
        int attempt = 0;
        while (true) {
            try {
                return task.get();
            } catch (OpenAiHttpException ex) {
                if (ex.statusCode == 429) {
                    if (attempt >= maxAttempts - 1) throw ex;
                    long wait = backoffsMs[Math.min(attempt, backoffsMs.length - 1)];
                    System.err.println("⚠️ Rate limit 429. Retrying in " + wait + " ms (" + (attempt+1) + "/" + maxAttempts + ")");
                    Thread.sleep(wait);
                    attempt++;
                } else {
                    throw ex;
                }
            }
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private String cleanJsonResponse(String json) {
        if (json == null) return "{}";
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replace("```json", "").replace("```", "").trim();
        }
        int start = json.indexOf("{");
        int end = json.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return json.substring(start, end + 1);
        }
        return "{}";
    }

    private ExtractionResult saveFallback(UUID fileId, UUID runId, long t0, String warning) throws Exception {
        ExtractionResult fallback = new ExtractionResult(
                fileId.toString(),
                runId.toString(),
                "generic_form",
                List.of(),
                List.of(warning),
                System.currentTimeMillis() - t0
        );

        ExtractionEntity ex = new ExtractionEntity();
        ex.setRunId(runId);
        ex.setDocumentType("generic_form");
        ex.setResultJson(om.writeValueAsString(fallback));
        ex.setAvgConfidence(0.0);
        extractionRepo.save(ex);

        var run = runRepo.findById(runId).orElseThrow();
        run.setStatus(RunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runRepo.save(run);

        return fallback;
    }

    // Dynamic average confidence from Map fields
    private Double averageConfidence(ExtractionResult r) {
        if (r == null || r.pages() == null) return null;

        return r.pages().stream()
                .filter(Objects::nonNull)
                .flatMap(p -> p.fields() == null ? Stream.empty() : p.fields().values().stream())
                .filter(Objects::nonNull)
                .mapToDouble(val -> val instanceof Number n ? n.doubleValue() : 0.0)
                .average()
                .orElse(0.0);
    }
}
