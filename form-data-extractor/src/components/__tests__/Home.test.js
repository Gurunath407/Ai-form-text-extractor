import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Home from '../Home';
import axios from 'axios';

// Mock axios
jest.mock('axios');

describe('Home Component', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    test('renders header and upload section', () => {
        render(<Home />);
        expect(screen.getByText(/Form Data Extractor/i)).toBeInTheDocument();
        expect(screen.getByText(/Upload & Process/i)).toBeInTheDocument();
        expect(screen.getByText(/Extract/i)).toBeInTheDocument();
    });

    test('handles image upload and extraction flow', async () => {
        // Mock responses
        axios.post.mockResolvedValueOnce({ data: { runId: 'test-run-123' } }); // upload
        axios.get.mockResolvedValueOnce({ data: { status: 'processing' } }); // start extraction
        axios.post.mockResolvedValueOnce({ data: { field1: 'value1' } }); // get result (first try success)
        axios.get.mockResolvedValue({ data: [] }); // fetch all extractions

        render(<Home />);

        // Simulate file upload
        const file = new File(['dummy content'], 'test.png', { type: 'image/png' });
        const input = screen.getByLabelText(/Upload Image/i);

        // Use fireEvent.change for file input
        fireEvent.change(input, { target: { files: [file] } });

        // Check if preview is shown (implied by state change, but we can check if Extract button is enabled)
        const extractButton = screen.getByText('Extract');
        expect(extractButton).not.toBeDisabled();

        // Click extract
        fireEvent.click(extractButton);

        // Verify axios calls
        await waitFor(() => {
            expect(axios.post).toHaveBeenCalledWith(
                'http://localhost:8080/v1/uploads',
                expect.any(FormData),
                expect.any(Object)
            );
        });

        // Verify status messages appear
        await waitFor(() => {
            expect(screen.getByText(/Image upload completed/i)).toBeInTheDocument();
        });
    });
});
