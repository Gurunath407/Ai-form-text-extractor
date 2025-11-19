import os
import shutil
import json
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse
from pydantic import BaseModel
from dotenv import load_dotenv
from agent import HandwritingExtractionAgent

# Load .env file from the backend directory
env_path = Path(__file__).parent / ".env"
load_dotenv(dotenv_path=env_path)

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".pdf"}
MAX_FILE_SIZE = 10 * 1024 * 1024

agent = None

class JSONToTableRequest(BaseModel):
    data: dict | list
    table_format: str = "html"

def flatten_dict(d: dict, parent_key: str = "", sep: str = "_") -> dict:
    """Flatten nested dictionaries"""
    items = []
    for k, v in d.items():
        new_key = f"{parent_key}{sep}{k}" if parent_key else k
        if isinstance(v, dict):
            items.extend(flatten_dict(v, new_key, sep=sep).items())
        elif isinstance(v, list):
            items.append((new_key, str(v)))
        else:
            items.append((new_key, v))
    return dict(items)

def json_to_html_table(data: dict | list) -> str:
    """Convert JSON to HTML table"""
    if isinstance(data, list):
        if not data:
            return "<table><tr><td>No data</td></tr></table>"
        if isinstance(data[0], dict):
            rows = [flatten_dict(item) for item in data]
        else:
            rows = [{"value": item} for item in data]
    else:
        rows = [flatten_dict(data)]
    
    if not rows:
        return "<table><tr><td>No data</td></tr></table>"
    
    all_keys = []
    for row in rows:
        all_keys.extend([k for k in row.keys() if k not in all_keys])
    
    html = "<table border='1' cellpadding='5' cellspacing='0' style='border-collapse: collapse;'>\n<thead>\n<tr>\n"
    for key in all_keys:
        html += f"<th>{key}</th>\n"
    html += "</tr>\n</thead>\n<tbody>\n"
    
    for row in rows:
        html += "<tr>\n"
        for key in all_keys:
            value = row.get(key, "")
            html += f"<td>{value}</td>\n"
        html += "</tr>\n"
    
    html += "</tbody>\n</table>"
    return html

def json_to_csv(data: dict | list) -> str:
    """Convert JSON to CSV format"""
    import csv
    from io import StringIO
    
    if isinstance(data, list):
        if not data:
            return ""
        if isinstance(data[0], dict):
            rows = [flatten_dict(item) for item in data]
        else:
            rows = [{"value": item} for item in data]
    else:
        rows = [flatten_dict(data)]
    
    if not rows:
        return ""
    
    all_keys = []
    for row in rows:
        all_keys.extend([k for k in row.keys() if k not in all_keys])
    
    output = StringIO()
    writer = csv.DictWriter(output, fieldnames=all_keys)
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()

def json_to_markdown_table(data: dict | list) -> str:
    """Convert JSON to Markdown table"""
    if isinstance(data, list):
        if not data:
            return "| No data |"
        if isinstance(data[0], dict):
            rows = [flatten_dict(item) for item in data]
        else:
            rows = [{"value": item} for item in data]
    else:
        rows = [flatten_dict(data)]
    
    if not rows:
        return "| No data |"
    
    all_keys = []
    for row in rows:
        all_keys.extend([k for k in row.keys() if k not in all_keys])
    
    markdown = "| " + " | ".join(all_keys) + " |\n"
    markdown += "| " + " | ".join(["---"] * len(all_keys)) + " |\n"
    
    for row in rows:
        values = [str(row.get(key, "")) for key in all_keys]
        markdown += "| " + " | ".join(values) + " |\n"
    
    return markdown

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    global agent
    try:
        agent = HandwritingExtractionAgent()
        print("[OK] Handwriting Extraction Agent initialized")
    except Exception as e:
        print(f"[WARNING] Agent initialization failed: {e}")
        print("Please ensure Ollama is running and reachable (see README)")
    yield
    # Shutdown (if needed)
    pass

app = FastAPI(
    title="Handwriting Extraction API",
    description="AI-powered handwritten text extraction using Ollama (vision model) and Langfuse",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
async def root():
    return {
        "message": "Handwriting Extraction API",
        "version": "1.0.0",
        "endpoints": {
            "/upload": "POST - Upload handwritten image for extraction",
            "/health": "GET - Health check",
            "/convert-to-table": "POST - Convert JSON data to table format (html, csv, markdown)"
        }
    }

@app.get("/health")
async def health_check():
    ollama_host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    ollama_model = os.getenv("OLLAMA_MODEL", "llava")
    return {
        "status": "healthy",
        "agent_initialized": agent is not None,
        "ollama_host": ollama_host,
        "ollama_model": ollama_model,
        "langfuse_configured": bool(os.getenv("LANGFUSE_PUBLIC_KEY") and os.getenv("LANGFUSE_SECRET_KEY"))
    }

@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    if not agent:
        raise HTTPException(
            status_code=503,
            detail="Agent not initialized. Please ensure the Ollama service is running."
        )
    
    filename = file.filename or "unknown.jpg"
    file_ext = Path(filename).suffix.lower()
    if file_ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"File type {file_ext} not allowed. Allowed types: {', '.join(ALLOWED_EXTENSIONS)}"
        )
    
    if file_ext == ".pdf":
        raise HTTPException(
            status_code=400,
            detail="PDF processing requires additional setup with poppler-utils. Please upload JPG or PNG images."
        )
    
    file_path = None
    try:
        contents = await file.read()
        
        if len(contents) > MAX_FILE_SIZE:
            raise HTTPException(
                status_code=400,
                detail=f"File size exceeds maximum allowed size of {MAX_FILE_SIZE / (1024*1024)}MB"
            )
        
        file_path = UPLOAD_DIR / filename
        with open(file_path, "wb") as f:
            f.write(contents)
        
        result = agent.extract_handwriting(str(file_path), filename)
        
        try:
            os.remove(file_path)
        except Exception as e:
            print(f"[WARNING] Failed to delete temporary file: {e}")
        
        if result["success"]:
            formatted_result = {
                "success": result["success"],
                "filename": result["filename"],
                "message": result["message"],
                "extracted_data": result["extracted_data"]
            }
            return JSONResponse(
                content=formatted_result,
                media_type="application/json"
            )
        else:
            raise HTTPException(status_code=500, detail=result.get("error", "Extraction failed"))
            
    except HTTPException:
        raise
    except Exception as e:
        import traceback
        error_details = str(e)
        error_type = type(e).__name__
        print(f"[ERROR] Upload error: {error_type}: {error_details}")
        print(f"[ERROR] Traceback: {traceback.format_exc()}")
        
        if file_path and file_path.exists():
            try:
                os.remove(file_path)
            except:
                pass
        
        raise HTTPException(
            status_code=500,
            detail=f"Error processing file: {error_type}: {error_details}"
        )

@app.delete("/cleanup")
async def cleanup_uploads():
    try:
        deleted = 0
        for file_path in UPLOAD_DIR.glob("*"):
            if file_path.is_file():
                os.remove(file_path)
                deleted += 1
        return {"message": f"Cleaned up {deleted} files"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/convert-to-table")
async def convert_json_to_table(request: JSONToTableRequest):
    try:
        table_format = request.table_format.lower()
        
        if table_format == "html":
            content = json_to_html_table(request.data)
            return HTMLResponse(content=content)
        elif table_format == "csv":
            content = json_to_csv(request.data)
            return {
                "format": "csv",
                "content": content
            }
        elif table_format == "markdown":
            content = json_to_markdown_table(request.data)
            return {
                "format": "markdown",
                "content": content
            }
        else:
            raise HTTPException(
                status_code=400,
                detail=f"Unsupported format: {table_format}. Supported formats: html, csv, markdown"
            )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error converting JSON to table: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
