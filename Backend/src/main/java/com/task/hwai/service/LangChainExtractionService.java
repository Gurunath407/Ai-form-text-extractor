package com.task.hwai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.hwai.model.ExtractionResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
public class LangChainExtractionService {

    private final ChatModel chatModel;
    private final OcrTool ocr;
    private final ObjectMapper om = new ObjectMapper();
    private final Tracer tracer;

    public LangChainExtractionService(
            ChatModel chatModel,
            OcrTool ocr,
            Tracer tracer
    ) {
        this.chatModel = chatModel;
        this.ocr = ocr;
        this.tracer = tracer;
    }

    public ExtractionResult extractFromImage(
            String fileId,
            String runId,
            Path imagePath
    ) throws Exception {

        long t0 = System.currentTimeMillis();

        Span root = tracer.spanBuilder("langchain.extraction")
                .setAttribute("file.id", fileId)
                .setAttribute("run.id", runId)
                .startSpan();

        try {
            // Step 1: OCR Extraction
            var ocrSpan = tracer.spanBuilder("ocr.extract").startSpan();
            List<OcrTool.PageResult> pages;
            try {
                pages = ocr.extract(imagePath);
            } finally {
                ocrSpan.end();
            }

            Map<String, Object> payload = Map.of("pages", pages);


            // Step 2: LangChain LLM Call
            Span llmSpan = tracer.spanBuilder("llm.structuring").startSpan();
            String rawOutput;
            try {
                // Make llmSpan the current span and add attributes to it
                try (var scope = llmSpan.makeCurrent()) {
                    rawOutput = extractUsingLangChain(payload, fileId, runId, llmSpan, root);
                }
            } finally {
                llmSpan.end();
            }

            if (rawOutput == null || rawOutput.isBlank()) {
                return createFallbackResult(fileId, runId, t0, "LLM returned null or empty response");
            }

            // Step 3: Clean and Parse JSON
            String json = cleanJsonResponse(rawOutput);
            System.out.println("LANGCHAIN CLEANED JSON --> " + json);

            // Step 4: Parse into ExtractionResult
            ExtractionResult result;
            try {
                result = om.readValue(json, ExtractionResult.class);
            } catch (Exception e) {
                System.err.println("❌ INVALID JSON FROM LANGCHAIN LLM");
                e.printStackTrace();
                return createFallbackResult(fileId, runId, t0, "LLM returned invalid JSON");
            }

            // Step 5: Enforce IDs
            result = new ExtractionResult(
                    fileId,
                    runId,
                    result.documentType() == null ? "generic_form" : result.documentType(),
                    result.pages(),
                    result.warnings(),
                    System.currentTimeMillis() - t0
            );

            System.out.println("✅ LangChain extraction successful for file: " + fileId);
            return result;

        } finally {
            root.end();
        }
    }

    private String extractUsingLangChain(
            Map<String, Object> ocrPayload,
            String fileId,
            String runId,
            Span llmSpan,
            Span rootSpan
    ) throws Exception {

        String systemPrompt = buildSystemPrompt();
        String userPayload = om.writeValueAsString(ocrPayload);

        List<ChatMessage> messages = Arrays.asList(
                new SystemMessage(systemPrompt),
                new UserMessage("OCR_DATA:\n" + userPayload)
        );

        // Set Langfuse-specific attributes on the LLM span
        String fullPrompt = systemPrompt + "\n\nOCR_DATA:\n" + userPayload;
        
        // Truncate if too long (Langfuse/OpenTelemetry may have limits)
        String inputForLangfuse = fullPrompt.length() > 10000 ? fullPrompt.substring(0, 10000) + "... (truncated)" : fullPrompt;
        
        // Set TRACE-LEVEL attributes on ROOT span (this is what Langfuse displays)
        rootSpan.setAttribute("input", inputForLangfuse);
        
        // Also set observation-level attributes on the LLM span
        llmSpan.setAttribute("langfuse.observation.input", inputForLangfuse);
        llmSpan.setAttribute("langfuse.observation.type", "generation");
        llmSpan.setAttribute("langfuse.observation.model", System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o"));
        
        System.out.println("[LANGFUSE] Setting trace input on ROOT span (length: " + inputForLangfuse.length() + ")");

        try{
            ChatResponse response = chatModel.chat(messages);
            String responseText = response.aiMessage().text();
            
            // Truncate response if too long
            String outputForLangfuse = responseText.length() > 10000 ? responseText.substring(0, 10000) + "... (truncated)" : responseText;
            
            // Set TRACE-LEVEL output on ROOT span
            rootSpan.setAttribute("output", outputForLangfuse);
            
            // Also set observation-level attributes on LLM span
            llmSpan.setAttribute("langfuse.observation.output", outputForLangfuse);
            llmSpan.setAttribute("langfuse.observation.usage.input", response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0);
            llmSpan.setAttribute("langfuse.observation.usage.output", response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0);
            
            System.out.println("[LANGFUSE] Setting trace output on ROOT span (length: " + outputForLangfuse.length() + ")");
            System.out.println("[LANGFUSE] Token usage - Input: " + (response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0) + ", Output: " + (response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0));
            
            return responseText;
        } catch (Exception ex) {
            llmSpan.setAttribute("error", true);
            llmSpan.setAttribute("error.message", ex.getMessage());
            System.err.println("❌ LangChain LLM call failed: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a strict information extraction engine powered by LangChain.
                Convert OCR text into structured JSON ONLY.
                
                RULES:
                - Output ONLY VALID JSON.
                - No explanations, no markdown formatting, no extra text.
                - Follow EXACT schema structure below:
                
                {
                  "file_id": string,
                  "run_id": string,
                  "document_type": string,
                  "pages": [
                    {
                      "page": number,
                      "fields": {any dynamic keys},
                      "tables": [any dynamic keys]
                    }
                  ],
                  "warnings": [string],
                  "processing_time_ms": number
                }
                
                IMPORTANT:
                - Extract all visible text and data from the OCR results
                - Preserve document structure and grouping
                - Use descriptive field names based on actual content
                - For tables, extract rows and columns accurately
                - If data is unclear or partially illegible, mark as "unreadable" instead of guessing
                """;
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

    private ExtractionResult createFallbackResult(
            String fileId,
            String runId,
            long t0,
            String warning
    ) throws Exception {
        return new ExtractionResult(
                fileId,
                runId,
                "generic_form",
                List.of(),
                List.of(warning),
                System.currentTimeMillis() - t0
        );
    }
}
