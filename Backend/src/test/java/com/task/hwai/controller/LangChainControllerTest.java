package com.task.hwai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.hwai.model.ExtractionResult;
import com.task.hwai.service.LangChainAgentService;
import com.task.hwai.service.LangChainExtractionService;
import com.task.hwai.service.StructuredExtractionParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LangChainController.class)
public class LangChainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LangChainExtractionService extractionService;

    @MockBean
    private LangChainAgentService agentService;

    @MockBean
    private StructuredExtractionParser parser;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testSimpleExtraction() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String imagePath = "test/path/image.png";

        ExtractionResult mockResult = new ExtractionResult(
                fileId.toString(),
                runId.toString(),
                "invoice",
                List.of(),
                List.of(),
                100L
        );

        when(extractionService.extractFromImage(any(), any(), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/v1/langchain/extract/simple")
                        .param("fileId", fileId.toString())
                        .param("runId", runId.toString())
                        .param("imagePath", imagePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentType").value("invoice"));
    }

    @Test
    public void testStructuredExtraction() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Map<String, String> request = Map.of("ocr_text", "Sample OCR Text");

        ExtractionResult mockResult = new ExtractionResult(
                fileId.toString(),
                runId.toString(),
                "form",
                List.of(),
                List.of(),
                50L
        );

        when(parser.parseStructuredExtraction(any(), any(), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/v1/langchain/extract/structured")
                        .param("fileId", fileId.toString())
                        .param("runId", runId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentType").value("form"));
    }
}
