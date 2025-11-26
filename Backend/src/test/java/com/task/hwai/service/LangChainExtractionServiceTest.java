package com.task.hwai.service;

import com.task.hwai.model.ExtractionResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LangChainExtractionServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private OcrTool ocr;

    @Mock
    private Tracer tracer;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    private LangChainExtractionService service;

    @BeforeEach
    public void setUp() {
        // Mock Tracer behavior
        lenient().when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.startSpan()).thenReturn(span);
        
        service = new LangChainExtractionService(chatModel, ocr, tracer);
    }

    @Test
    public void testExtractFromImage_Success() throws Exception {
        // Mock OCR
        List<OcrTool.PageResult> pages = List.of(
            new OcrTool.PageResult(1, List.of(), null)
        );
        when(ocr.extract(any())).thenReturn(pages);

        // Mock ChatModel
        String jsonResponse = """
                {
                  "file_id": "file-123",
                  "run_id": "run-456",
                  "document_type": "invoice",
                  "pages": [],
                  "warnings": [],
                  "processing_time_ms": 100
                }
                """;
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(jsonResponse))
                .build();
        when(chatModel.chat(anyList())).thenReturn(chatResponse);

        // Execute
        ExtractionResult result = service.extractFromImage("file-123", "run-456", Path.of("test.png"));

        // Verify
        assertNotNull(result);
        assertEquals("invoice", result.documentType());
        assertEquals("file-123", result.fileId());
        
        verify(ocr).extract(any());
        verify(chatModel).chat(anyList());
    }

    @Test
    public void testExtractFromImage_InvalidJson() throws Exception {
        // Mock OCR
        when(ocr.extract(any())).thenReturn(List.of());

        // Mock ChatModel with invalid JSON
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Invalid JSON"))
                .build();
        when(chatModel.chat(anyList())).thenReturn(chatResponse);

        // Execute
        ExtractionResult result = service.extractFromImage("file-123", "run-456", Path.of("test.png"));

        // Verify fallback result
        assertNotNull(result);
        assertEquals("generic_form", result.documentType());
        assertTrue(result.warnings().contains("LLM returned invalid JSON"));
    }
}
