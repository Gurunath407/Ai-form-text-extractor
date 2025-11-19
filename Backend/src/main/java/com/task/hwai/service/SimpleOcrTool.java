package com.task.hwai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class SimpleOcrTool implements OcrTool {

    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();
    private final String pythonOcrUrl; // updated default to Docker service name

    public SimpleOcrTool(
            OkHttpClient http,
            @Value("${ocr.python.url:http://python-service:8000/upload}") String pythonOcrUrl
    ) {
        this.http = http;
        this.pythonOcrUrl = pythonOcrUrl;
    }

    @Override
    public List<PageResult> extract(Path path) throws Exception {
        File file = path.toFile();
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + path);

        // Build multipart request
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("image/*"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(pythonOcrUrl)
                .post(requestBody)
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Python OCR server returned: " + response.code() + " - " + response.message());
            }

            String body = response.body() == null ? "" : response.body().string();
            JsonNode root = om.readTree(body);

            JsonNode extracted = null;
            if (root.has("extracted_data")) extracted = root.get("extracted_data");
            else if (root.has("ocr-extract")) extracted = root.get("ocr-extract");
            else if (root.has("text")) extracted = root.get("text");
            else extracted = root;

            List<PageResult> pages = new ArrayList<>();

            if (extracted.has("pages") && extracted.get("pages").isArray()) {
                int idx = 1;
                for (JsonNode pageNode : extracted.get("pages")) {
                    List<OcrTool.Token> tokens = new ArrayList<>();
                    List<Map<String,Object>> rawBlocks = new ArrayList<>();

                    if (pageNode.has("fields")) {
                        Map<String,Object> fieldsMap = om.convertValue(pageNode.get("fields"), Map.class);
                        rawBlocks.add(fieldsMap);
                        for (Object v : fieldsMap.values()) {
                            if (v != null) tokens.add(new OcrTool.Token(v.toString(), 0.0f));
                        }
                    }

                    if (pageNode.has("text")) {
                        tokens.add(new OcrTool.Token(pageNode.get("text").asText(""), 0.0f));
                    }
                    if (pageNode.has("lines") && pageNode.get("lines").isArray()) {
                        for (JsonNode ln : pageNode.get("lines")) tokens.add(new OcrTool.Token(ln.asText(""), 0.0f));
                    }

                    pages.add(new PageResult(idx++, tokens, rawBlocks));
                }

            } else if (extracted.isTextual()) {
                pages.add(new PageResult(1, List.of(new OcrTool.Token(extracted.asText(""), 0.0f)), List.of(Map.of("text", extracted.asText("")))));
            } else if (extracted.isObject()) {
                List<OcrTool.Token> tokens = new ArrayList<>();
                List<Map<String,Object>> rawBlocks = new ArrayList<>();
                if (extracted.has("paragraphs") && extracted.get("paragraphs").isArray()) {
                    for (JsonNode p : extracted.get("paragraphs")) tokens.add(new OcrTool.Token(p.asText(""), 0.0f));
                    rawBlocks.add(om.convertValue(extracted.get("paragraphs"), Map.class));
                } else {
                    rawBlocks.add(om.convertValue(extracted, Map.class));
                    Iterator<Map.Entry<String, JsonNode>> it = extracted.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        tokens.add(new OcrTool.Token(e.getKey() + ": " + e.getValue().asText(""), 0.0f));
                    }
                }
                pages.add(new PageResult(1, tokens, rawBlocks));
            } else {
                pages.add(new PageResult(1, List.of(new OcrTool.Token("", 0.0f)), List.of()));
            }

            return pages;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to call Python OCR server: " + ex.getMessage(), ex);
        }
    }
}
