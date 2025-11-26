package com.task.hwai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.hwai.model.ExtractionResult;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LangChainAgentService {

    private final ChatModel chatModel;
    private final OcrTool ocr;
    private final ObjectMapper om = new ObjectMapper();
    private final Tracer tracer;

    private String cachedOcrData;

    public LangChainAgentService(
            ChatModel chatModel,
            OcrTool ocr,
            Tracer tracer
    ) {
        this.chatModel = chatModel;
        this.ocr = ocr;
        this.tracer = tracer;
    }

    public ExtractionResult processWithAgent(
            String fileId,
            String runId,
            Path imagePath
    ) throws Exception {

        long t0 = System.currentTimeMillis();

        var root = tracer.spanBuilder("agent.process")
                .setAttribute("file.id", fileId)
                .setAttribute("run.id", runId)
                .startSpan();

        try {
            // Extract OCR data
            var ocrSpan = tracer.spanBuilder("ocr.extract").startSpan();
            List<OcrTool.PageResult> pages;
            try {
                pages = ocr.extract(imagePath);
                this.cachedOcrData = om.writeValueAsString(Map.of("pages", pages));
            } finally {
                ocrSpan.end();
            }

            // Invoke agent tools
            var agentSpan = tracer.spanBuilder("agent.invoke").startSpan();
            String extractedJson;
            try {
                extractedJson = invokeAgentTools(fileId, runId);
            } finally {
                agentSpan.end();
            }

            // Parse result
            String cleanJson = cleanJsonResponse(extractedJson);
            System.out.println("AGENT EXTRACTED JSON --> " + cleanJson);

            ExtractionResult result;
            try {
                result = om.readValue(cleanJson, ExtractionResult.class);
            } catch (Exception e) {
                System.err.println("❌ AGENT: INVALID JSON");
                e.printStackTrace();
                return createFallback(fileId, runId, t0, "Agent returned invalid JSON");
            }

            result = new ExtractionResult(
                    fileId,
                    runId,
                    result.documentType() == null ? "generic_form" : result.documentType(),
                    result.pages(),
                    result.warnings(),
                    System.currentTimeMillis() - t0
            );

            System.out.println("✅ LangChain Agent extraction successful");
            return result;

        } finally {
            root.end();
        }
    }

    private String invokeAgentTools(String fileId, String runId) {
        // Demonstrate multi-step extraction with different "tools"
        Map<String, String> toolResults = new LinkedHashMap<>();

        // Tool 1: Analyze document structure
        toolResults.put("structure_analysis", analyzeDocumentStructure());

        // Tool 2: Extract form fields
        toolResults.put("form_fields", extractFormFields());

        // Tool 3: Detect tables
        toolResults.put("tables", detectTables());

        // Tool 4: Generate structured output
        return generateStructuredOutput(fileId, runId, toolResults);
    }

    @Tool(value = "Analyze the overall document structure and type")
    private String analyzeDocumentStructure() {
        try {
            Map<String, Object> data = om.readValue(cachedOcrData, Map.class);
            List<?> pages = (List<?>) data.get("pages");
            
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("page_count", pages.size());
            analysis.put("structure", "multi_page_form");
            analysis.put("confidence", 0.95);
            
            return om.writeValueAsString(analysis);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Tool(value = "Extract form field names and values from OCR data")
    private String extractFormFields() {
        try {
            Map<String, Object> data = om.readValue(cachedOcrData, Map.class);
            List<?> pages = (List<?>) data.get("pages");
            
            Map<String, Object> fields = new HashMap<>();
            
            if (pages != null && !pages.isEmpty()) {
                Map<String, Object> firstPage = (Map<String, Object>) pages.get(0);
                Object text = firstPage.get("text");
                
                if (text != null) {
                    String textStr = text.toString();
                    // Simple field extraction: key: value pattern
                    Pattern pattern = Pattern.compile("([\\w\\s]+):\\s*([^\\n]+)");
                    Matcher matcher = pattern.matcher(textStr);
                    
                    while (matcher.find()) {
                        String key = matcher.group(1).trim().toLowerCase().replace(" ", "_");
                        String value = matcher.group(2).trim();
                        fields.put(key, value);
                    }
                }
            }
            
            return om.writeValueAsString(fields);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Tool(value = "Detect and extract tables from the document")
    private String detectTables() {
        try {
            Map<String, Object> data = om.readValue(cachedOcrData, Map.class);
            List<?> pages = (List<?>) data.get("pages");
            
            List<Map<String, Object>> tables = new ArrayList<>();
            
            // Placeholder: In real implementation, use table detection algorithm
            Map<String, Object> tableInfo = new HashMap<>();
            tableInfo.put("detected", true);
            tableInfo.put("count", 0);
            tableInfo.put("description", "No tables detected in this OCR data");
            
            tables.add(tableInfo);
            
            return om.writeValueAsString(tables);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Tool(value = "Generate final structured JSON output from all tool results")
    private String generateStructuredOutput(
            String fileId,
            String runId,
            Map<String, String> toolResults
    ) {
        try {
            List<ExtractionResult.Page> pages = new ArrayList<>();
            
            var pageFields = om.readValue(toolResults.get("form_fields"), Map.class);
            var tables = om.readValue(toolResults.get("tables"), List.class);
            
            pages.add(new ExtractionResult.Page(
                    1,
                    pageFields,
                    tables
            ));
            
            ExtractionResult result = new ExtractionResult(
                    fileId,
                    runId,
                    "generic_form",
                    pages,
                    List.of(),
                    0
            );
            
            return om.writeValueAsString(result);
        } catch (Exception e) {
            return "{}";
        }
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

    private ExtractionResult createFallback(
            String fileId,
            String runId,
            long t0,
            String warning
    ) {
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
