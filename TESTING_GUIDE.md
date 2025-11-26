# Testing Guide - AI Form Data Extractor

This document provides comprehensive instructions for running all tests in the AI Form Data Extractor project.

## Overview

The project now includes comprehensive test coverage across all components:

1. **Frontend Tests** (React Testing Library + Jest)
2. **Backend Tests** (JUnit + Mockito)
3. **OCR Service Tests** (pytest)
4. **End-to-End Tests** (Playwright)

## Prerequisites

### Frontend Testing
- Node.js and npm installed
- Dependencies installed: `cd form-data-extractor && npm install`

### Backend Testing
- Java 21
- Maven 3.x
- Dependencies will be downloaded automatically by Maven

### OCR Service Testing
- Python 3.8+
- Install test dependencies: `cd ocr-service && pip install -r requirements.txt`

### Playwright E2E Testing
- Node.js and npm installed
- Playwright browsers: `cd e2e && npx playwright install`

## Running Tests

### 1. Frontend Tests

```bash
cd form-data-extractor
npm test -- --watchAll=false
```

**Test Files:**
- `src/App.test.js` - Tests App component rendering
- `src/components/__tests__/Home.test.js` - Tests Home component with mocked axios

**What's Tested:**
- Component rendering
- File upload functionality
- Button states (enabled/disabled)
- API interaction mocking

### 2. Backend Tests

```bash
cd Backend
mvn test
```

**Test Files:**
- `src/test/java/com/task/hwai/controller/LangChainControllerTest.java` - Controller endpoint tests
- `src/test/java/com/task/hwai/service/LangChainExtractionServiceTest.java` - Service layer tests

**What's Tested:**
- REST API endpoints
- Service layer logic
- Mock dependencies (ChatModel, OcrTool, Tracer)

**Note:** Backend tests may require additional configuration. If tests fail, ensure:
- All dependencies are properly resolved
- Spring Boot context loads correctly
- Mock objects are properly configured

### 3. OCR Service Tests

```bash
cd ocr-service
pytest tests/
```

**Test Files:**
- `tests/test_main.py` - FastAPI endpoint tests

**What's Tested:**
- `/` root endpoint
- `/health` health check
- `/upload` file upload validation
- `/convert-to-table` JSON conversion (HTML, CSV, Markdown)

**Test Features:**
- Async testing with `pytest-asyncio`
- HTTP client testing with `httpx`
- Mock agent to avoid actual OCR processing

### 4. Playwright E2E Tests

**Important:** Before running E2E tests, ensure all services are running:

```bash
# Terminal 1 - Frontend
cd form-data-extractor
npm start

# Terminal 2 - Backend
cd Backend
mvn spring-boot:run

# Terminal 3 - OCR Service
cd ocr-service
python main.py
```

Then run Playwright tests:

```bash
cd e2e
npm test
```

**Test Files:**
- `tests/frontend.spec.js` - Frontend UI tests
- `tests/backend.spec.js` - Backend API tests
- `tests/ocr-service.spec.js` - OCR service API tests

**What's Tested:**
- Frontend UI rendering and interactions
- Backend API endpoints (health, validation)
- OCR service endpoints (health, file validation, conversions)

**View Test Results:**
```bash
npx playwright show-report
```

## Test Coverage Summary

| Component | Test Type | Files | Status |
|-----------|-----------|-------|--------|
| Frontend | Unit/Integration | 2 | ✅ Created |
| Backend | Unit/Integration | 2 | ⚠️ Needs verification |
| OCR Service | Unit/Integration | 1 | ✅ Created |
| E2E | Playwright | 3 | ✅ Created |

## Troubleshooting

### Frontend Tests
- **Issue:** `react-scripts not found`
  - **Solution:** Run `npm install` in `form-data-extractor` directory

### Backend Tests
- **Issue:** Compilation errors
  - **Solution:** Ensure `spring-boot-starter-test` dependency is in `pom.xml`
  - Run `mvn clean install` to refresh dependencies

### OCR Service Tests
- **Issue:** Import errors
  - **Solution:** Install test dependencies: `pip install pytest pytest-asyncio httpx`

### Playwright Tests
- **Issue:** Connection refused errors
  - **Solution:** Ensure all services (Frontend, Backend, OCR) are running before executing E2E tests

## Continuous Integration

For CI/CD pipelines, run tests in this order:

```bash
# 1. Backend tests
cd Backend && mvn test

# 2. OCR service tests
cd ../ocr-service && pytest

# 3. Frontend tests
cd ../form-data-extractor && npm test -- --watchAll=false

# 4. E2E tests (requires services running)
cd ../e2e && npm test
```

## Next Steps

1. **Increase Coverage:** Add more test cases for edge cases
2. **Integration Tests:** Add tests that verify component integration
3. **Performance Tests:** Add load testing for API endpoints
4. **Code Coverage:** Configure coverage reporting (JaCoCo for Java, Jest coverage for React, pytest-cov for Python)
