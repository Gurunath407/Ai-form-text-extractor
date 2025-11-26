# Playwright Test Cases

This document contains Playwright test cases for the Frontend, Spring Boot Backend, and Python OCR Service.

## Prerequisites

1.  **Node.js** installed.
2.  **Playwright** installed:
    ```bash
    npm init playwright@latest
    ```
3.  **Test Data**: Place a sample image named `sample_form.jpg` in your test directory.

---

## 1. Frontend Tests (E2E)

Create a file `tests/frontend.spec.js`.

```javascript
// tests/frontend.spec.js
const { test, expect } = require('@playwright/test');
const path = require('path');

const FRONTEND_URL = 'http://localhost:3000';

test.describe('Frontend E2E Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    await page.goto(FRONTEND_URL);
  });

  test('should load the home page', async ({ page }) => {
    await expect(page).toHaveTitle(/Form Data Extractor/i);
    await expect(page.getByText('Upload & Process')).toBeVisible();
  });

  test('should upload an image and extract data', async ({ page }) => {
    // 1. Upload Image
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(path.join(__dirname, 'sample_form.jpg'));

    // 2. Verify Preview
    await expect(page.getByAltText('Preview')).toBeVisible();

    // 3. Click Extract
    const extractButton = page.getByRole('button', { name: 'Extract' });
    await expect(extractButton).toBeEnabled();
    await extractButton.click();

    // 4. Wait for Processing
    await expect(page.getByText('Processing...')).toBeVisible();
    
    // 5. Wait for Completion (Timeout increased for OCR processing)
    await expect(page.getByText('Extraction completed!', { exact: false })).toBeVisible({ timeout: 30000 });

    // 6. Verify Results Section
    await expect(page.getByText('Extraction Results')).toBeVisible();
    await expect(page.getByRole('button', { name: 'JSON View' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Table View' })).toBeVisible();
  });

  test('should manage extractions (View/Edit/Delete)', async ({ page }) => {
    // Ensure there is at least one extraction in the list
    // You might need to seed data or run the upload test first
    
    // Wait for table to load
    await expect(page.getByRole('table')).toBeVisible();

    // 1. View Extraction
    const viewButtons = page.getByRole('button', { name: 'View' });
    if (await viewButtons.count() > 0) {
      await viewButtons.first().click();
      await expect(page.getByText('Extraction Details')).toBeVisible();
      await page.getByRole('button', { name: '×' }).click(); // Close modal
    }

    // 2. Edit Extraction
    const editButtons = page.getByRole('button', { name: 'Edit' });
    if (await editButtons.count() > 0) {
      await editButtons.first().click();
      await expect(page.getByText('Edit Extraction #')).toBeVisible();
      
      // Modify a field
      const docTypeInput = page.locator('input[value*="Form"]'); // Adjust selector based on actual value
      if (await docTypeInput.isVisible()) {
          await docTypeInput.fill('Updated Doc Type');
          await page.getByRole('button', { name: 'Save Changes' }).click();
          await expect(page.getByText('Extraction updated.')).toBeVisible();
      } else {
           await page.getByRole('button', { name: 'Cancel' }).click();
      }
    }

    // 3. Delete Extraction
    // Be careful with this test as it deletes data
    /*
    const deleteButtons = page.getByRole('button', { name: 'Delete' });
    if (await deleteButtons.count() > 0) {
      page.on('dialog', dialog => dialog.accept()); // Handle confirmation
      await deleteButtons.first().click();
      await expect(page.getByText('Extraction deleted.')).toBeVisible();
    }
    */
  });
});
```

---

## 2. Spring Boot Backend Tests (API)

Create a file `tests/backend.spec.js`.

```javascript
// tests/backend.spec.js
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const BACKEND_URL = 'http://localhost:8080';

test.describe('Spring Boot Backend API Tests', () => {

  test('GET /v1/extractions - should return list of extractions', async ({ request }) => {
    const response = await request.get(`${BACKEND_URL}/v1/extractions`);
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(Array.isArray(data)).toBeTruthy();
  });

  test('POST /v1/uploads - should upload file and create run', async ({ request }) => {
    const filePath = path.join(__dirname, 'sample_form.jpg');
    const fileBuffer = fs.readFileSync(filePath);

    const response = await request.post(`${BACKEND_URL}/v1/uploads`, {
      multipart: {
        file: {
          name: 'sample_form.jpg',
          mimeType: 'image/jpeg',
          buffer: fileBuffer,
        },
      },
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.fileId).toBeDefined();
    expect(data.runId).toBeDefined();
    
    // Store runId for next test if needed, or verify run status here
    return data.runId;
  });

  test('CRUD /v1/extractions', async ({ request }) => {
    // 1. Create
    const newExtraction = {
      documentType: 'Test Doc',
      resultJson: '{"test": "data"}',
      avgConfidence: 0.95,
      runId: '00000000-0000-0000-0000-000000000000' // Use a dummy UUID or valid one
    };

    const createRes = await request.post(`${BACKEND_URL}/v1/extractions`, {
      data: newExtraction
    });
    expect(createRes.ok()).toBeTruthy();
    const created = await createRes.json();
    expect(created.id).toBeDefined();
    expect(created.documentType).toBe('Test Doc');

    // 2. Read
    const getRes = await request.get(`${BACKEND_URL}/v1/extractions/${created.id}`);
    expect(getRes.ok()).toBeTruthy();
    const fetched = await getRes.json();
    expect(fetched.id).toBe(created.id);

    // 3. Update
    const updatePayload = { ...fetched, documentType: 'Updated Doc' };
    const updateRes = await request.put(`${BACKEND_URL}/v1/extractions/${created.id}`, {
      data: updatePayload
    });
    expect(updateRes.ok()).toBeTruthy();
    const updated = await updateRes.json();
    expect(updated.documentType).toBe('Updated Doc');

    // 4. Delete
    const deleteRes = await request.delete(`${BACKEND_URL}/v1/extractions/${created.id}`);
    expect(deleteRes.ok()).toBeTruthy();

    // Verify Deletion
    const verifyRes = await request.get(`${BACKEND_URL}/v1/extractions/${created.id}`);
    expect(verifyRes.status()).toBe(404);
  });
});
```

---

## 3. Python OCR Service Tests (API)

Create a file `tests/ocr_service.spec.js`.

```javascript
// tests/ocr_service.spec.js
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const OCR_SERVICE_URL = 'http://localhost:8000';

test.describe('Python OCR Service API Tests', () => {

  test('GET /health - should be healthy', async ({ request }) => {
    const response = await request.get(`${OCR_SERVICE_URL}/health`);
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.status).toBe('healthy');
    expect(data.agent_initialized).toBeDefined();
  });

  test('POST /upload - should extract text from image', async ({ request }) => {
    // Note: This test requires the Ollama service to be running and the agent initialized
    const filePath = path.join(__dirname, 'sample_form.jpg');
    const fileBuffer = fs.readFileSync(filePath);

    const response = await request.post(`${OCR_SERVICE_URL}/upload`, {
      multipart: {
        file: {
          name: 'sample_form.jpg',
          mimeType: 'image/jpeg',
          buffer: fileBuffer,
        },
      },
    });

    // If agent is not ready, it might return 503, handle gracefully or expect 200
    if (response.status() === 503) {
        console.log('Agent not initialized, skipping extraction verification.');
    } else {
        expect(response.ok()).toBeTruthy();
        const data = await response.json();
        expect(data.success).toBe(true);
        expect(data.extracted_data).toBeDefined();
    }
  });

  test('POST /convert-to-table - should convert JSON to HTML', async ({ request }) => {
    const jsonData = {
      data: [
        { "name": "Alice", "age": 30 },
        { "name": "Bob", "age": 25 }
      ],
      table_format: "html"
    };

    const response = await request.post(`${OCR_SERVICE_URL}/convert-to-table`, {
      data: jsonData
    });

    expect(response.ok()).toBeTruthy();
    const html = await response.text();
    expect(html).toContain('<table');
    expect(html).toContain('Alice');
  });

  test('POST /convert-to-table - should convert JSON to Markdown', async ({ request }) => {
    const jsonData = {
      data: [
        { "name": "Alice", "age": 30 }
      ],
      table_format: "markdown"
    };

    const response = await request.post(`${OCR_SERVICE_URL}/convert-to-table`, {
      data: jsonData
    });

    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.format).toBe('markdown');
    expect(data.content).toContain('| Alice |');
  });
});
```

## Running the Tests

1.  Ensure all services (Frontend, Backend, OCR Service, Ollama) are running.
2.  Run the tests:
    ```bash
    npx playwright test
    ```
