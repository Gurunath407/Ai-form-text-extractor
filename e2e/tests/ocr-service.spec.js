const { test, expect } = require('@playwright/test');

test.describe('OCR Service - Python FastAPI', () => {
    const BASE_URL = 'http://localhost:8000';

    test('should return API info from root endpoint', async ({ request }) => {
        const response = await request.get(`${BASE_URL}/`);

        expect(response.ok()).toBeTruthy();
        const data = await response.json();

        expect(data.message).toBe('Handwriting Extraction API');
        expect(data.version).toBe('1.0.0');
        expect(data.endpoints).toBeDefined();
    });

    test('should return health status', async ({ request }) => {
        const response = await request.get(`${BASE_URL}/health`);

        expect(response.ok()).toBeTruthy();
        const data = await response.json();

        expect(data.status).toBe('healthy');
        expect(data).toHaveProperty('agent_initialized');
        expect(data).toHaveProperty('ollama_host');
    });

    test('should reject invalid file types', async ({ request }) => {
        // Create a text file
        const response = await request.post(`${BASE_URL}/upload`, {
            multipart: {
                file: {
                    name: 'test.txt',
                    mimeType: 'text/plain',
                    buffer: Buffer.from('test content')
                }
            }
        });

        expect(response.status()).toBe(400);
        const data = await response.json();
        expect(data.detail).toContain('not allowed');
    });

    test('should convert JSON to HTML table', async ({ request }) => {
        const response = await request.post(`${BASE_URL}/convert-to-table`, {
            data: {
                data: [{ name: 'John', age: 30 }],
                table_format: 'html'
            }
        });

        expect(response.ok()).toBeTruthy();
        const html = await response.text();
        expect(html).toContain('<table');
        expect(html).toContain('John');
    });

    test('should convert JSON to CSV', async ({ request }) => {
        const response = await request.post(`${BASE_URL}/convert-to-table`, {
            data: {
                data: [{ name: 'Jane', age: 25 }],
                table_format: 'csv'
            }
        });

        expect(response.ok()).toBeTruthy();
        const data = await response.json();
        expect(data.format).toBe('csv');
        expect(data.content).toContain('name');
    });
});
