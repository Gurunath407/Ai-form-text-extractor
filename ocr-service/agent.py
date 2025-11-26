import os
import base64
import json
from typing import Dict, Any, Optional, List
import requests
from PIL import Image, ImageEnhance, ImageFilter
import io
from langfuse import Langfuse
from openai import OpenAI
import hashlib
import time

try:
    from groq import Groq
except ImportError:
    Groq = None
try:
    from langfuse.callback import CallbackHandler  # type: ignore
except ImportError:
    CallbackHandler = None


class HandwritingExtractionAgent:
    def __init__(self):
        self.ollama_host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
        self.ollama_model = os.getenv("OLLAMA_MODEL", "bakllava:latest")
        self.langfuse_public_key = os.getenv("LANGFUSE_PUBLIC_KEY")
        self.langfuse_secret_key = os.getenv("LANGFUSE_SECRET_KEY")
        self.langfuse_host = os.getenv("LANGFUSE_HOST", "https://cloud.langfuse.com")
        self.request_timeout = float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "120"))
        self.temperature = float(os.getenv("OLLAMA_TEMPERATURE", "0.1"))
        self.num_predict = int(os.getenv("OLLAMA_NUM_PREDICT", "2048"))
        self.enable_preprocessing = os.getenv("ENABLE_IMAGE_PREPROCESSING", "true").lower() == "true"
        self.use_consensus = os.getenv("USE_CONSENSUS_MODE", "true").lower() == "true"
        
        # Feedback & learning directories
        self.feedback_dir = os.getenv("FEEDBACK_DIR", "./handwriting_feedback")
        self.corrections_cache_dir = os.path.join(self.feedback_dir, "corrections")
        os.makedirs(self.feedback_dir, exist_ok=True)
        os.makedirs(self.corrections_cache_dir, exist_ok=True)
        
        self.hf_token = os.getenv("HF_TOKEN")
        if self.hf_token:
            self.hf_client = OpenAI(
                base_url="https://router.huggingface.co/v1",
                api_key=self.hf_token,
            )
        else:
            self.hf_client = None
        
        self.groq_api_key = os.getenv("GROQ_API_KEY")
        if self.groq_api_key and self.groq_api_key.strip() and self.groq_api_key != "your_groq_api_key_here" and Groq:
            try:
                self.groq_client = Groq(api_key=self.groq_api_key)
            except Exception as e:
                print(f"[WARNING] Failed to initialize Groq client: {e}")
                self.groq_client = None
        else:
            self.groq_client = None
        
        self.langfuse: Optional[Langfuse] = None
        self.langfuse_handler = None
        
        if self.langfuse_public_key and self.langfuse_secret_key:
            try:
                self.langfuse = Langfuse(
                    public_key=self.langfuse_public_key,
                    secret_key=self.langfuse_secret_key,
                    host=self.langfuse_host
                )
                if CallbackHandler:
                    self.langfuse_handler = CallbackHandler(
                        public_key=self.langfuse_public_key,
                        secret_key=self.langfuse_secret_key,
                        host=self.langfuse_host
                    )
                print("[OK] Langfuse initialized successfully")
            except Exception as e:
                print(f"[WARNING] Langfuse initialization failed: {e}")
                print("Continuing without Langfuse tracing...")
        else:
            print("[WARNING] Langfuse credentials not found. Continuing without tracing...")
        
        if self.hf_client:
            print("[OK] HuggingFace API configured (Qwen2.5-VL-7B-Instruct)")
        else:
            print("[WARNING] HuggingFace token not configured")
        
        if self.groq_client:
            print("[OK] Groq API configured (Qwen-2.5-32b)")
        else:
            print("[WARNING] Groq API key not configured")
        
        print(f"[OK] Ollama configured (host={self.ollama_host}, model={self.ollama_model})")

    def _get_image_hash(self, image_path: str) -> str:
        """Generate a stable hash for the image to identify it."""
        with open(image_path, "rb") as f:
            return hashlib.sha256(f.read()).hexdigest()[:16]

    def _get_cached_correction(self, image_hash: str) -> Optional[Dict[str, Any]]:
        """Check if a corrected extraction exists for this image."""
        cache_path = os.path.join(self.corrections_cache_dir, f"{image_hash}.json")
        if os.path.exists(cache_path):
            try:
                with open(cache_path, "r") as f:
                    return json.load(f)
            except Exception as e:
                print(f"[WARNING] Failed to load cached correction: {e}")
        return None

    def _save_correction(self, image_hash: str, corrected_data: Dict[str, Any], original_image_path: str):
        """Save a human-corrected extraction for future use."""
        cache_path = os.path.join(self.corrections_cache_dir, f"{image_hash}.json")
        metadata = {
            "corrected_at": time.time(),
            "original_image": os.path.basename(original_image_path),
            "data": corrected_data
        }
        with open(cache_path, "w") as f:
            json.dump(metadata, f, indent=2)
        print(f"[INFO] Saved correction for image hash {image_hash}")

    def _log_for_finetuning(self, image_path: str, prompt: str, model_output: str, source: str):
        """Log high-quality examples to Langfuse for fine-tuning dataset."""
        if not self.langfuse:
            return
        try:
            image_hash = self._get_image_hash(image_path)
            self.langfuse.trace(
                name="handwriting_finetune_example",
                input={"image_hash": image_hash, "prompt": prompt, "source": source},
                output={"model_response": model_output}
            )
        except Exception as e:
            print(f"[WARNING] Failed to log for fine-tuning: {e}")

    def preprocess_image(self, image_path: str) -> Image.Image:
        img = Image.open(image_path)
        if img.mode != 'RGB':
            img = img.convert('RGB')
        enhancer = ImageEnhance.Contrast(img)
        img = enhancer.enhance(1.5)
        enhancer = ImageEnhance.Sharpness(img)
        img = enhancer.enhance(1.3)
        img = img.filter(ImageFilter.MedianFilter(size=3))
        width, height = img.size
        min_size = 512
        if max(width, height) < min_size:
            scale = min_size / max(width, height)
            new_width = int(width * scale)
            new_height = int(height * scale)
            img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
        return img

    def encode_image(self, image_path: str) -> str:
        if self.enable_preprocessing:
            try:
                img = self.preprocess_image(image_path)
                buffer = io.BytesIO()
                img.save(buffer, format='JPEG', quality=95)
                image_bytes = buffer.getvalue()
            except Exception as e:
                print(f"[WARNING] Image preprocessing failed, using original: {e}")
                with open(image_path, "rb") as image_file:
                    image_bytes = image_file.read()
        else:
            with open(image_path, "rb") as image_file:
                image_bytes = image_file.read()
        return base64.b64encode(image_bytes).decode('utf-8')

    def _build_prompt(self) -> str:
        return """You are an expert OCR system specialized in reading handwritten text with maximum accuracy.

Analyze this handwritten document with extreme care and extract ALL the information you can see.

CRITICAL INSTRUCTIONS FOR MAXIMUM ACCURACY:
1. Read each character and word carefully - examine the image in detail
2. DO NOT assume or hallucinate any fields - only extract what is clearly visible
3. Pay special attention to:
   - Numbers (phone numbers, dates, policy numbers, etc.) - read each digit precisely
   - Names - read each letter carefully, including capitalization
   - Addresses - read street names, numbers, and city names accurately
   - Email addresses - verify @ symbols and domain names
4. For partially readable text, extract what you can see clearly, even if incomplete
5. If text is completely illegible or blank, mark it as "unreadable" (not null)
6. Return the data as clean, structured JSON with proper nesting
7. Create field names based on actual labels, headings, and form structure you see
8. Preserve the exact logical structure and grouping of information
9. Be extremely precise with values - read numbers and text character by character
10. Double-check your extraction before returning the JSON

IMPORTANT: Read slowly and carefully. Accuracy is more important than speed.
Return ONLY valid JSON with no additional text, markdown, or explanation before or after.
The JSON should have descriptive keys based on the actual content structure."""

    def extract_handwriting(self, image_path: str, filename: str) -> Dict[str, Any]:
        try:
            print(f"[DEBUG] Starting extraction for {filename}")
            # Check for cached correction first
            image_hash = self._get_image_hash(image_path)
            cached = self._get_cached_correction(image_hash)
            if cached:
                print(f"[INFO] Using cached correction for {filename}")
                return {
                    "success": True,
                    "filename": filename,
                    "extracted_data": cached["data"],
                    "message": "Used human-corrected result from cache",
                    "source": "cached_correction"
                }

            # Prefer HuggingFace (supports vision)
            if self.hf_client:
                print(f"[DEBUG] Using HuggingFace for {filename}")
                hf_result = self.extract_handwriting_huggingface(image_path, filename)
                if self.use_consensus and self.groq_client:
                    # Groq can't do vision, so consensus only if we had multiple vision models
                    # For now, just return HF result
                    return hf_result
                else:
                    return hf_result
            else:
                # Fallback to Groq (will fail on images, but included for completeness)
                print(f"[DEBUG] Falling back to Groq for {filename}")
                return self.extract_handwriting_groq(image_path, filename)
        except Exception as e:
            import traceback
            print(f"[ERROR] extract_handwriting failed for {filename}: {type(e).__name__}: {str(e)}")
            print(f"[ERROR] Traceback: {traceback.format_exc()}")
            return {
                "success": False,
                "filename": filename,
                "error": str(e),
                "message": f"Extraction failed: {type(e).__name__}",
                "source": "error"
            }

    def extract_handwriting_huggingface(self, image_path: str, filename: str) -> Dict[str, Any]:
        if not self.hf_client:
            return {
                "success": False,
                "filename": filename,
                "error": "HuggingFace token not configured",
                "message": "HF_TOKEN environment variable not set"
            }

        try:
            with open(image_path, "rb") as image_file:
                image_data = base64.standard_b64encode(image_file.read()).decode("utf-8")

            prompt = self._build_prompt()
            completion = self.hf_client.chat.completions.create(
                model="Qwen/Qwen2.5-VL-7B-Instruct:hyperbolic",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_data}"}}
                        ]
                    }
                ],
            )

            extracted_text = completion.choices[0].message.content
            structured_data = self._parse_json_response(extracted_text)

            result = {
                "success": True,
                "filename": filename,
                "extracted_data": structured_data,
                "message": "Handwriting extracted successfully using HuggingFace",
                "source": "huggingface_qwen_vl"
            }

            # Log for potential fine-tuning
            self._log_for_finetuning(image_path, prompt, extracted_text, "huggingface_qwen_vl")



            return result

        except Exception as e:
            error_result = {
                "success": False,
                "filename": filename,
                "error": "Groq API key not configured",
                "message": "GROQ_API_KEY environment variable not set"
            }

        # Groq models are text-only — cannot process images
        if self.hf_client:
            print("[INFO] Groq doesn't support vision, falling back to HuggingFace vision model")
            return self.extract_handwriting_huggingface(image_path, filename)

        return {
            "success": False,
            "filename": filename,
            "error": "Groq API models don't support vision/image inputs",
            "message": "Please configure HF_TOKEN for HuggingFace vision model (Qwen2.5-VL-7B-Instruct) to process images"
        }

    def _parse_json_response(self, text: str) -> Dict[str, Any]:
        extracted_text = text.strip()
        if extracted_text.startswith("```json"):
            extracted_text = extracted_text[7:]
        if extracted_text.startswith("```"):
            extracted_text = extracted_text[3:]
        if extracted_text.endswith("```"):
            extracted_text = extracted_text[:-3]
        extracted_text = extracted_text.strip()

        try:
            return json.loads(extracted_text)
        except json.JSONDecodeError:
            return {"raw_text": extracted_text}

    def provide_feedback(self, image_path: str, filename: str, corrected_data: Dict[str, Any]):
        """
        Provide human feedback to correct an extraction.
        This "trains" the system by caching the correction.
        """
        image_hash = self._get_image_hash(image_path)
        self._save_correction(image_hash, corrected_data, image_path)

        # Also log to Langfuse as ground truth
        if self.langfuse:
            try:
                self.langfuse.trace(
                    name="handwriting_correction",
                    input={"image_path": filename, "image_hash": image_hash},
                    output={"corrected_data": corrected_data}
                )
            except Exception as e:
                print(f"[WARNING] Failed to log correction to Langfuse: {e}")

        print(f"[SUCCESS] Feedback accepted for {filename}. Future requests will use this correction.")

    def export_finetune_dataset(self) -> List[Dict[str, Any]]:
        """
        Export all human-corrected examples as a fine-tuning dataset.
        Returns list of { "image_hash", "prompt", "completion" }.
        Note: Actual image bytes are not included for privacy/security.
        """
        dataset = []
        for file in os.listdir(self.corrections_cache_dir):
            if file.endswith(".json"):
                path = os.path.join(self.corrections_cache_dir, file)
                try:
                    with open(path, "r") as f:
                        data = json.load(f)
                        dataset.append({
                            "image_hash": file.replace(".json", ""),
                            "completion": data["data"],
                            "metadata": {
                                "corrected_at": data.get("corrected_at"),
                                "original_image": data.get("original_image")
                            }
                        })
                except Exception as e:
                    print(f"[WARNING] Skipping corrupted feedback file {file}: {e}")
        return dataset
