# LangChain Integration Guide

## Overview

Your Spring Boot backend has been enhanced with comprehensive LangChain4j integration for intelligent document extraction and processing. The integration provides three primary approaches to data extraction:

1. **Simple Extraction** - Direct LLM-based JSON structuring
2. **Agent-Based Extraction** - Multi-tool orchestration with specialized tasks
3. **Structured Parsing** - Advanced parsing with confidence scoring

## Components Added

### 1. **Enhanced LangchainConfig** (`config/LangchainConfig.java`)
- Updated with LangChain4j's `ChatLanguageModel` bean
- Configured with OpenAI integration
- Supports custom model selection via `OPENAI_MODEL` environment variable
- Default model: `gpt-4o`

### 2. **LangChainExtractionService** (`service/LangChainExtractionService.java`)
- Provides direct LLM-based extraction
- Integrates OCR results with LangChain4j prompts
- Includes automatic JSON cleaning and parsing
- Built-in error handling and fallback mechanisms

### 3. **LangChainAgentService** (`service/LangChainAgentService.java`)
- Orchestrates multiple extraction tools
- Uses `@Tool` annotations for specialized tasks:
  - Document structure analysis
  - Form field extraction
  - Table detection
  - Structured output generation
- Multi-step processing with tracing

### 4. **StructuredExtractionParser** (`service/StructuredExtractionParser.java`)
- Advanced parsing with confidence scoring
- Document type detection
- Field-level confidence heuristics
- Table detection from OCR text
- Automatic field and structure inference

### 5. **LangChainController** (`controller/LangChainController.java`)
- RESTful endpoints for all extraction methods
- Exposed at `/v1/langchain/`

## API Endpoints

### 1. Simple Extraction
```bash
POST /v1/langchain/extract/simple
Parameters:
  - fileId: UUID
  - runId: UUID
  - imagePath: string (file path)

Response:
{
  "success": true,
  "data": { ExtractionResult },
  "method": "langchain_simple"
}
```

### 2. Agent-Based Extraction
```bash
POST /v1/langchain/extract/agent
Parameters:
  - fileId: UUID
  - runId: UUID
  - imagePath: string (file path)

Response:
{
  "success": true,
  "data": { ExtractionResult },
  "method": "langchain_agent_with_tools"
}
```

### 3. Structured Extraction
```bash
POST /v1/langchain/extract/structured
Parameters:
  - fileId: UUID
  - runId: UUID

Body:
{
  "ocr_text": "Raw OCR text from the document"
}

Response:
{
  "success": true,
  "data": { ExtractionResult },
  "method": "structured_parser_with_confidence"
}
```

### 4. Parse Fields with Confidence
```bash
POST /v1/langchain/parse/fields

Body:
{
  "ocr_text": "Raw OCR text"
}

Response:
{
  "success": true,
  "data": {
    "fields": { "field_name": "value" },
    "confidence_scores": { "field_name": 0.95 }
  }
}
```

### 5. Parse Tables
```bash
POST /v1/langchain/parse/tables

Body:
{
  "ocr_text": "Raw OCR text containing table data"
}

Response:
{
  "success": true,
  "tables": [
    {
      "name": "Table_1",
      "rows": [ { "column_1": "value" } ]
    }
  ]
}
```

### 6. Health Check
```bash
GET /v1/langchain/health

Response:
{
  "status": "healthy",
  "langchain_version": "1.8.0",
  "features": [
    "simple_extraction",
    "agent_with_tools",
    "structured_parsing",
    "field_confidence_scoring",
    "table_detection"
  ]
}
```

## Environment Configuration

### Required Variables
```bash
OPENAI_API_KEY=sk-...  # Your OpenAI API key
OPENAI_MODEL=gpt-4o    # Optional: model name (default: gpt-4o)
```

### Optional Variables
```bash
OLLAMA_HOST=http://localhost:11434     # Ollama server for fallback
OLLAMA_MODEL=bakllava:latest           # Ollama model
OLLAMA_TIMEOUT_SECONDS=120             # Request timeout
OPENAI_TEMPERATURE=0.0                 # LLM temperature
```

## Integration with Existing Code

### Using LangChainExtractionService in Your Code

```java
@Autowired
private LangChainExtractionService extractionService;

// In your service method
public ExtractionResult extract(UUID fileId, UUID runId, Path imagePath) throws Exception {
    return extractionService.extractFromImage(
        fileId.toString(),
        runId.toString(),
        imagePath
    );
}
```

### Using LangChainAgentService

```java
@Autowired
private LangChainAgentService agentService;

// Multi-tool extraction
public ExtractionResult processWithAgent(UUID fileId, UUID runId, Path imagePath) throws Exception {
    return agentService.processWithAgent(
        fileId.toString(),
        runId.toString(),
        imagePath
    );
}
```

### Using StructuredExtractionParser

```java
@Autowired
private StructuredExtractionParser parser;

// Parse OCR text with structure inference
public ExtractionResult parseStructured(String ocrText, UUID fileId, UUID runId) throws Exception {
    return parser.parseStructuredExtraction(ocrText, fileId.toString(), runId.toString());
}

// Get fields with confidence scores
public Map<String, Object> getFieldsWithConfidence(String ocrText) {
    return parser.parseFieldsWithConfidence(ocrText);
}

// Detect tables in OCR text
public List<Map<String, Object>> getTables(String ocrText) {
    return parser.parseTablesFromOcr(ocrText);
}
```

## Comparison of Methods

| Method | Pros | Cons | Best For |
|--------|------|------|----------|
| **Simple** | Fast, simple, single LLM call | Less flexible | Quick document extraction |
| **Agent** | Modular, multi-step, specialized tools | More complex, slower | Complex document structures |
| **Structured** | Smart document type detection, confidence scoring | Requires OCR text | Forms with varying structures |

## Features

### 1. OpenTelemetry Tracing
All services include distributed tracing for monitoring:
- `agent.run` - Main agent span
- `ocr.extract` - OCR extraction span
- `llm.structuring` - LLM processing span

### 2. Error Handling
- Automatic retry logic for rate limits (429)
- Fallback mechanisms for failed extractions
- Comprehensive error messages

### 3. JSON Cleaning
Automatic cleanup of:
- Markdown code blocks (```json...```)
- Invalid JSON formatting
- Extraction of JSON from mixed text

### 4. Document Type Detection
Automatic detection for:
- Applications
- Forms
- Claims
- Invoices
- Receipts
- Contracts
- Insurance documents
- Passports
- Licenses
- Certificates
- Reports
- Statements

## Performance Tips

1. **Use Simple Extraction for** - Standard forms with predictable structure
2. **Use Agent Extraction for** - Complex multi-page documents with tables
3. **Use Structured Parsing for** - Unknown document types with auto-detection
4. **Cache Results** - Store extracted data by document hash to avoid re-processing

## Troubleshooting

### LLM Call Fails
1. Verify `OPENAI_API_KEY` is set correctly
2. Check OpenAI API quota and rate limits
3. Ensure internet connectivity
4. Check model name is valid

### Invalid JSON Response
1. Verify OCR quality (high-quality images work better)
2. Try with `gpt-4o` model instead of `gpt-3.5-turbo`
3. Increase `maxTokens` if extraction is cut off
4. Reduce temperature to 0.0 for deterministic output

### Timeout Issues
1. Increase `OLLAMA_TIMEOUT_SECONDS` for large documents
2. Check network latency to OpenAI
3. Consider splitting large documents into pages

## Dependencies

- `dev.langchain4j:langchain4j:1.8.0`
- `dev.langchain4j:langchain4j-open-ai:1.8.0`
- Spring Boot 3.3.4
- Java 21+

## Next Steps

1. Test endpoints with sample documents
2. Integrate LangChain services into your existing extraction pipeline
3. Monitor performance and adjust parameters as needed
4. Consider implementing caching for frequently processed documents

## Additional Resources

- [LangChain4j Documentation](https://github.com/langchain4j/langchain4j)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
