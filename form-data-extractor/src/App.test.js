import { render, screen } from '@testing-library/react';
import App from './App';

// Mock Home component to avoid full rendering in App test
jest.mock('./components/Home', () => () => <div>Mock Home Component</div>);

test('renders Home component', () => {
  render(<App />);
  const homeElement = screen.getByText(/Mock Home Component/i);
  expect(homeElement).toBeInTheDocument();
});
