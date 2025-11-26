import pytest
from httpx import AsyncClient
from fastapi import status
from main import app
from pathlib import Path
from unittest.mock import Mock, patch, AsyncMock
import json


@pytest.fixture
def mock_agent():
    """Mock HandwritingExtractionAgent"""
    with patch('main.agent') as mock:
        mock.extract_handwriting = Mock(return_value={
            "success": True,
            "filename": "test.jpg",
            "message": "Extraction successful",
            "extracted_data": {"field1": "value1"}
        })
        yield mock


@pytest.mark.asyncio
async def test_root_endpoint():
    """Test root endpoint returns API info"""
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.get("/")
    
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert "message" in data
    assert "version" in data
    assert data["version"] == "1.0.0"


@pytest.mark.asyncio
async def test_health_check():
    """Test health check endpoint"""
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.get("/health")
    
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert "status" in data
    assert data["status"] == "healthy"
    assert "agent_initialized" in data


@pytest.mark.asyncio
async def test_upload_file_success(mock_agent):
    """Test successful file upload and extraction"""
    # Create a mock file
    file_content = b"fake image content"
    files = {"file": ("test.jpg", file_content, "image/jpeg")}
    
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.post("/upload", files=files)
    
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["success"] is True
    assert "extracted_data" in data


@pytest.mark.asyncio
async def test_upload_invalid_file_type():
    """Test upload with invalid file type"""
    file_content = b"fake content"
    files = {"file": ("test.txt", file_content, "text/plain")}
    
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.post("/upload", files=files)
    
    assert response.status_code == status.HTTP_400_BAD_REQUEST
    assert "not allowed" in response.json()["detail"]


@pytest.mark.asyncio
async def test_convert_to_html_table():
    """Test JSON to HTML table conversion"""
    request_data = {
        "data": [{"name": "John", "age": 30}, {"name": "Jane", "age": 25}],
        "table_format": "html"
    }
    
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.post("/convert-to-table", json=request_data)
    
    assert response.status_code == status.HTTP_200_OK
    assert "<table" in response.text
    assert "John" in response.text


@pytest.mark.asyncio
async def test_convert_to_csv():
    """Test JSON to CSV conversion"""
    request_data = {
        "data": [{"name": "John", "age": 30}],
        "table_format": "csv"
    }
    
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.post("/convert-to-table", json=request_data)
    
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["format"] == "csv"
    assert "name" in data["content"]
