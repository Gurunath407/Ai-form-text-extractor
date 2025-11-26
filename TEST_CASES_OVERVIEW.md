# Test Cases Overview

This document provides a detailed overview of the test cases for the Form Data Extractor project, covering Frontend, Backend, and OCR Service.

> **Note:** This document is formatted in Markdown. You can easily convert this to PDF using a Markdown-to-PDF converter (e.g., in VS Code using the "Markdown PDF" extension or online tools).

---

## 1. Frontend Test Cases (E2E)

These tests verify the user interface and end-to-end flows using Playwright.

| Test Case ID | Test Case Description | Inputs | Expected Output |
| :--- | :--- | :--- | :--- |
| **FE-01** | **Load Home Page** | URL: `http://localhost:3000` | - Page Title contains "Form Data Extractor"<br>- "Upload & Process" section is visible |
| **FE-02** | **Upload Image and Extract Data** | - File: `sample_form.jpg`<br>- Action: Click "Extract" button | - Preview image is visible<br>- "Processing..." status appears<br>- "Extraction completed!" message appears<br>- "Extraction Results" section becomes visible<br>- JSON and Table view buttons are visible |
| **FE-03** | **View Extraction Details** | - Action: Click "View" on an existing extraction in the list | - Modal opens with title "Extraction Details"<br>- JSON/Table data is displayed<br>- Modal closes when "×" is clicked |
| **FE-04** | **Edit Extraction** | - Action: Click "Edit" on an extraction<br>- Input: Change "Document Type" to "Updated Doc Type"<br>- Action: Click "Save Changes" | - "Extraction updated" message appears<br>- The extraction list reflects the new "Document Type" |
| **FE-05** | **Delete Extraction** | - Action: Click "Delete" on an extraction<br>- Action: Confirm dialog (if applicable) | - "Extraction deleted" message appears<br>- The extraction is removed from the list |

---

## 2. Backend Test Cases (Spring Boot API)

These tests verify the Spring Boot Backend REST API endpoints.

| Test Case ID | Test Case Description | Inputs | Expected Output |
| :--- | :--- | :--- | :--- |
| **BE-01** | **Get All Extractions** | - Endpoint: `GET /v1/extractions` | - Status Code: `200 OK`<br>- Body: Array of extraction objects |
| **BE-02** | **Upload File & Create Run** | - Endpoint: `POST /v1/uploads`<br>- Multipart File: `sample_form.jpg` | - Status Code: `200 OK`<br>- Body: JSON containing valid `fileId` and `runId` |
| **BE-03** | **Create Extraction (CRUD)** | - Endpoint: `POST /v1/extractions`<br>- Body: JSON `{ "documentType": "Test Doc", ... }` | - Status Code: `200 OK`<br>- Body: Created extraction object with a generated `id` |
| **BE-04** | **Read Extraction (CRUD)** | - Endpoint: `GET /v1/extractions/{id}`<br>- Input: Valid Extraction ID | - Status Code: `200 OK`<br>- Body: Extraction object matching the ID |
| **BE-05** | **Update Extraction (CRUD)** | - Endpoint: `PUT /v1/extractions/{id}`<br>- Body: JSON `{ "documentType": "Updated Doc" }` | - Status Code: `200 OK`<br>- Body: Updated extraction object showing "Updated Doc" |
| **BE-06** | **Delete Extraction (CRUD)** | - Endpoint: `DELETE /v1/extractions/{id}`<br>- Input: Valid Extraction ID | - Status Code: `200 OK` (or `204 No Content`)<br>- Subsequent GET returns `404 Not Found` |

---

## 3. OCR Service Test Cases (Python API)

These tests verify the Python OCR Service endpoints.

| Test Case ID | Test Case Description | Inputs | Expected Output |
| :--- | :--- | :--- | :--- |
| **OCR-01** | **Health Check** | - Endpoint: `GET /health` | - Status Code: `200 OK`<br>- Body: `{ "status": "healthy", "agent_initialized": true }` |
| **OCR-02** | **Extract Text from Image** | - Endpoint: `POST /upload`<br>- Multipart File: `sample_form.jpg` | - Status Code: `200 OK`<br>- Body: `{ "success": true, "extracted_data": { ... } }` |
| **OCR-03** | **Convert JSON to HTML Table** | - Endpoint: `POST /convert-to-table`<br>- Body: `{ "data": [...], "table_format": "html" }` | - Status Code: `200 OK`<br>- Body: String containing valid HTML `<table>` tags |
| **OCR-04** | **Convert JSON to Markdown** | - Endpoint: `POST /convert-to-table`<br>- Body: `{ "data": [...], "table_format": "markdown" }` | - Status Code: `200 OK`<br>- Body: JSON `{ "format": "markdown", "content": "| ... |" }` |
