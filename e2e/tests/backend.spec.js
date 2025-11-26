const { test, expect } = require('@playwright/test');

test.describe('Backend - Spring Boot API', () => {
    const BASE_URL = 'http://localhost:8080';

    test('should return health status from LangChain endpoint', async ({ request }) => {
        const response = await request.get(`${BASE_URL}/v1/langchain/health`);

        expect(response.ok()).toBeTruthy();
        const data = await response.json();

        expect(data.status).toBe('healthy');
        expect(data.langchain_version).toBe('1.8.0');
        expect(data.features).toContain('simple_extraction');
    });

    test('should validate structured extraction endpoint requires ocr_text', async ({ request }) => {
        const response = await request.post(`${BASE_URL}/v1/langchain/extract/structured`, {
            params: {
                fileId: '123e4567-e89b-12d3-a456-426614174000',
                runId: '123e4567-e89b-12d3-a456-426614174001'
            },
            data: {
                ocr_text: ''
            }
        });

        expect(response.status()).toBe(400);
        const data = await response.json();
        expect(data.success).toBe(false);
        expect(data.error).toContain('ocr_text is required');
    });

    test('should validate parse fields endpoint requires ocr_text', async ({ request }) => {
        const response = await request.post(`${BASE_URL}/v1/langchain/parse/fields`, {
            data: {
                ocr_text: ''
            }
        });

        expect(response.status()).toBe(400);
        const data = await response.json();
        expect(data.success).toBe(false);
    });
});
