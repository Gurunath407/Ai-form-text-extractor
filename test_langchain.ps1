# LangChain Integration Test Script (PowerShell)
# This script tests all LangChain endpoints

$BASE_URL = "http://localhost:8080/v1/langchain"

Write-Host "🧪 Testing LangChain Integration..." -ForegroundColor Cyan
Write-Host ""

# Test 1: Health Check
Write-Host "1️⃣ Testing Health Endpoint..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$BASE_URL/health" -Method Get
$response | ConvertTo-Json -Depth 10
Write-Host ""

# Test 2: Parse Fields with Confidence
Write-Host "2️⃣ Testing Field Parsing with Confidence..." -ForegroundColor Yellow
$body = @{
    ocr_text = "First Name: John`nLast Name: Doe`nEmail: john.doe@example.com`nPhone: +1-555-123-4567`nAddress: 123 Main Street"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "$BASE_URL/parse/fields" -Method Post -Body $body -ContentType "application/json"
$response | ConvertTo-Json -Depth 10
Write-Host ""

# Test 3: Parse Tables
Write-Host "3️⃣ Testing Table Parsing..." -ForegroundColor Yellow
$body = @{
    ocr_text = "Product: Widget A: Price: `$10: Quantity: 5`nProduct: Widget B: Price: `$20: Quantity: 3`nProduct: Widget C: Price: `$15: Quantity: 7"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "$BASE_URL/parse/tables" -Method Post -Body $body -ContentType "application/json"
$response | ConvertTo-Json -Depth 10
Write-Host ""

# Test 4: Structured Extraction
Write-Host "4️⃣ Testing Structured Extraction..." -ForegroundColor Yellow
$fileId = "123e4567-e89b-12d3-a456-426614174000"
$runId = "987fcdeb-51a2-43f7-b123-456789abcdef"

$body = @{
    ocr_text = @"
Application Form

Personal Information:
Name: Jane Smith
Date of Birth: 1990-05-15
Email: jane.smith@email.com
Phone: 555-0123

Address:
Street: 456 Oak Avenue
City: Springfield
State: IL
Zip: 62701
"@
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "$BASE_URL/extract/structured?fileId=$fileId&runId=$runId" -Method Post -Body $body -ContentType "application/json"
$response | ConvertTo-Json -Depth 10
Write-Host ""

Write-Host "✅ All tests completed!" -ForegroundColor Green
Write-Host ""
Write-Host "Note: For image-based tests (extract/simple and extract/agent)," -ForegroundColor Cyan
Write-Host "you need to provide actual image paths on your system." -ForegroundColor Cyan
