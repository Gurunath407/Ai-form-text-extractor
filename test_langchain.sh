#!/bin/bash

# LangChain Integration Test Script
# This script tests all LangChain endpoints

BASE_URL="http://localhost:8080/v1/langchain"

echo "🧪 Testing LangChain Integration..."
echo ""

# Test 1: Health Check
echo "1️⃣ Testing Health Endpoint..."
curl -s "${BASE_URL}/health" | jq '.'
echo ""
echo ""

# Test 2: Parse Fields with Confidence
echo "2️⃣ Testing Field Parsing with Confidence..."
curl -s -X POST "${BASE_URL}/parse/fields" \
  -H "Content-Type: application/json" \
  -d '{
    "ocr_text": "First Name: John\nLast Name: Doe\nEmail: john.doe@example.com\nPhone: +1-555-123-4567\nAddress: 123 Main Street"
  }' | jq '.'
echo ""
echo ""

# Test 3: Parse Tables
echo "3️⃣ Testing Table Parsing..."
curl -s -X POST "${BASE_URL}/parse/tables" \
  -H "Content-Type: application/json" \
  -d '{
    "ocr_text": "Product: Widget A: Price: $10: Quantity: 5\nProduct: Widget B: Price: $20: Quantity: 3\nProduct: Widget C: Price: $15: Quantity: 7"
  }' | jq '.'
echo ""
echo ""

# Test 4: Structured Extraction
echo "4️⃣ Testing Structured Extraction..."
curl -s -X POST "${BASE_URL}/extract/structured?fileId=123e4567-e89b-12d3-a456-426614174000&runId=987fcdeb-51a2-43f7-b123-456789abcdef" \
  -H "Content-Type: application/json" \
  -d '{
    "ocr_text": "Application Form\n\nPersonal Information:\nName: Jane Smith\nDate of Birth: 1990-05-15\nEmail: jane.smith@email.com\nPhone: 555-0123\n\nAddress:\nStreet: 456 Oak Avenue\nCity: Springfield\nState: IL\nZip: 62701"
  }' | jq '.'
echo ""
echo ""

echo "✅ All tests completed!"
echo ""
echo "Note: For image-based tests (extract/simple and extract/agent),"
echo "you need to provide actual image paths on your system."
