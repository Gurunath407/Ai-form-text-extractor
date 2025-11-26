const { test, expect } = require('@playwright/test');

test.describe('Frontend - Form Data Extractor', () => {
    test.beforeEach(async ({ page }) => {
        // Navigate to the frontend
        await page.goto('http://localhost:3000');
    });

    test('should display the main header', async ({ page }) => {
        // Check if the header is visible
        await expect(page.locator('text=Form Data Extractor')).toBeVisible();
    });

    test('should display upload section', async ({ page }) => {
        // Check for upload section
        await expect(page.locator('text=Upload & Process')).toBeVisible();
        await expect(page.locator('input[type="file"]')).toBeVisible();
    });

    test('should have Extract button disabled initially', async ({ page }) => {
        // Extract button should be disabled when no file is selected
        const extractButton = page.locator('button:has-text("Extract")');
        await expect(extractButton).toBeDisabled();
    });

    test('should enable Extract button after file upload', async ({ page }) => {
        // Create a test file
        const fileInput = page.locator('input[type="file"]');

        // Upload a file
        await fileInput.setInputFiles({
            name: 'test.png',
            mimeType: 'image/png',
            buffer: Buffer.from('fake image content')
        });

        // Wait a bit for state update
        await page.waitForTimeout(500);

        // Extract button should now be enabled
        const extractButton = page.locator('button:has-text("Extract")');
        await expect(extractButton).not.toBeDisabled();
    });

    test('should display Manage All Extractions section', async ({ page }) => {
        // Check if the management section exists
        await expect(page.locator('text=Manage All Extractions')).toBeVisible();
    });
});
