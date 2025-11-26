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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StructuredExtractionParser {

    private final ChatModel chatModel;
    private final Tracer tracer;
    private final ObjectMapper om = new ObjectMapper();

    public StructuredExtractionParser(ChatModel chatModel, Tracer tracer) {
        this.chatModel = chatModel;
        this.tracer = tracer;
    }

    public ExtractionResult parseStructuredExtraction(
            String ocrText,
            String fileId,
            String runId
    ) throws Exception {

        // Step 1: Extract document metadata
        String documentType = extractDocumentType(ocrText);

        // Step 2: Use LLM to structure the data
        String structuredJson = generateStructuredJson(ocrText, documentType, fileId, runId);

        // Step 3: Parse and validate
        ExtractionResult result = om.readValue(structuredJson, ExtractionResult.class);

        // Step 4: Enforce correct IDs
        return new ExtractionResult(
                fileId,
                runId,
                result.documentType() == null ? documentType : result.documentType(),
                result.pages(),
                result.warnings(),
                result.processingTimeMs()
        );
    }

    private String extractDocumentType(String ocrText) {
        List<String> documentTypeIndicators = List.of(
                "application", "form", "claim", "invoice", "receipt", "contract", "agreement",
                "insurance", "passport", "license", "certificate", "report", "statement"
        );

        String lowerText = ocrText.toLowerCase();

        for (String indicator : documentTypeIndicators) {
            if (lowerText.contains(indicator)) {
                return indicator + "_document";
            }
        }

        return "generic_form";
    }

    private String generateStructuredJson(
            String ocrText,
            String documentType,
            String fileId,
            String runId
    ) throws Exception {

        String systemPrompt = buildStructuredExtractionPrompt(documentType);

        List<ChatMessage> messages = Arrays.asList(
                new SystemMessage(systemPrompt),
                new UserMessage(String.format("OCR_TEXT:\n%s", ocrText))
        );

        // Create root span for this extraction
        Span rootSpan = tracer.spanBuilder("structured.extraction")
                .setAttribute("file.id", fileId)
                .setAttribute("run.id", runId)
                .setAttribute("document.type", documentType)
                .startSpan();

        try {
            // Set Langfuse trace input
            String fullPrompt = systemPrompt + "\n\nOCR_TEXT:\n" + ocrText;
            String inputForLangfuse = fullPrompt.length() > 10000 ? fullPrompt.substring(0, 10000) + "... (truncated)" : fullPrompt;
            rootSpan.setAttribute("input", inputForLangfuse);
            System.out.println("[LANGFUSE] StructuredExtractionParser - Setting trace input (length: " + inputForLangfuse.length() + ")");

            ChatResponse response = chatModel.chat(messages);
            String content = response.aiMessage().text();

            // Set Langfuse trace output
            String outputForLangfuse = content.length() > 10000 ? content.substring(0, 10000) + "... (truncated)" : content;
            rootSpan.setAttribute("output", outputForLangfuse);
            System.out.println("[LANGFUSE] StructuredExtractionParser - Setting trace output (length: " + outputForLangfuse.length() + ")");

            // Clean and parse JSON
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replace("```json", "").replace("```", "").trim();
            }

            // Extract JSON object
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }

            return content;
        } catch (Exception ex) {
            rootSpan.setAttribute("error", true);
            rootSpan.setAttribute("error.message", ex.getMessage());
            System.err.println("❌ Structured extraction LLM call failed: " + ex.getMessage());
            throw ex;
        } finally {
            rootSpan.end();
        }
    }

    private String buildStructuredExtractionPrompt(String documentType) {
        return String.format("""
                You are a document structure expert specializing in %s documents.
                Extract ALL information from the OCR text and return it as VALID JSON ONLY.
                
                REQUIRED JSON STRUCTURE:
                {
                  "file_id": string,
                  "run_id": string,
                  "document_type": "%s",
                  "pages": [
                    {
                      "page": number (1-indexed),
                      "fields": {
                        "field_name": "field_value",
                        ...
                      },
                      "tables": [
                        {
                          "table_name": "name",
                          "rows": [
                            {"column1": "value", "column2": "value"},
                            ...
                          ]
                        }
                      ]
                    }
                  ],
                  "warnings": [string],
                  "processing_time_ms": number
                }
                
                EXTRACTION RULES:
                1. Extract EVERY visible field and its value
                2. Use descriptive field names based on labels in the document
                3. Group related fields logically by page
                4. For tables: identify headers and rows accurately
                5. For unreadable text: use "unreadable" instead of guessing
                6. For dates: standardize to YYYY-MM-DD format if possible
                7. For phone/postal codes: preserve original formatting
                8. Return ONLY valid JSON with no markdown, explanations, or extra text
                9. Do NOT include null values - omit them instead
                10. Warnings array: include any data quality issues (partially readable text, low confidence areas)
                
                IMPORTANT: The JSON must be parseable and valid. No markdown formatting.
                """, documentType, documentType);
    }

    public Map<String, Object> parseFieldsWithConfidence(String ocrText) {
        // Advanced parsing with confidence scoring
        Map<String, Object> result = new HashMap<>();
        result.put("fields", new HashMap<>());
        result.put("confidence_scores", new HashMap<>());

        // Pattern for "field: value" pairs
        Pattern pattern = Pattern.compile("([\\w\\s]+):\\s*([^\\n]+)");
        Matcher matcher = pattern.matcher(ocrText);

        Map<String, String> fields = new HashMap<>();
        Map<String, Double> confidences = new HashMap<>();

        while (matcher.find()) {
            String key = matcher.group(1).trim().toLowerCase().replace(" ", "_");
            String value = matcher.group(2).trim();

            fields.put(key, value);

            // Simple confidence heuristic: shorter fields are less confident
            double confidence = Math.min(1.0, value.length() / 50.0);
            confidences.put(key, confidence);
        }

        result.put("fields", fields);
        result.put("confidence_scores", confidences);

        return result;
    }

    public List<Map<String, Object>> parseTablesFromOcr(String ocrText) {
        List<Map<String, Object>> tables = new ArrayList<>();

        // Simple table detection: look for multiple consecutive fields
        String[] lines = ocrText.split("\n");
        Map<String, Object> currentTable = null;
        List<Map<String, String>> rows = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (currentTable != null && !rows.isEmpty()) {
                    currentTable.put("rows", rows);
                    tables.add(currentTable);
                    currentTable = null;
                    rows = new ArrayList<>();
                }
                continue;
            }

            // If line has multiple colons, it might be a table row
            int colonCount = (int) line.chars().filter(ch -> ch == ':').count();
            if (colonCount > 1) {
                if (currentTable == null) {
                    currentTable = new HashMap<>();
                    currentTable.put("name", "Table_" + (tables.size() + 1));
                }

                Map<String, String> row = new HashMap<>();
                String[] parts = line.split(":");
                for (int i = 0; i < parts.length - 1; i++) {
                    row.put("column_" + (i + 1), parts[i].trim());
                }
                rows.add(row);
            }
        }

        if (currentTable != null && !rows.isEmpty()) {
            currentTable.put("rows", rows);
            tables.add(currentTable);
        }

        return tables;
    }
}
