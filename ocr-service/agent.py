import os
import base64
import json
from typing import Dict, Any, Optional, List
from PIL import Image, ImageEnhance, ImageFilter
import io
import hashlib
import time

from openai import OpenAI

try:
    from groq import Groq
except ImportError:
    Groq = None



class HandwritingExtractionAgent:
    def __init__(self):
        """Initialize only HuggingFace (required) and Groq (optional)."""

        # ✔ HuggingFace key required
        self.hf_token = os.getenv("HF_TOKEN")
        if self.hf_token:
            self.hf_client = OpenAI(
                base_url="https://router.huggingface.co/v1",
                api_key=self.hf_token,
            )
            print("[OK] HuggingFace API configured (Qwen2.5-VL-7B-Instruct)")
        else:
            self.hf_client = None
            print("[WARNING] HuggingFace token not set — Vision OCR will fail")

        # ✔ Optional: Groq (text only, not used for OCR)
        groq_key = os.getenv("GROQ_API_KEY")
        if groq_key and Groq:
            try:
                self.groq_client = Groq(api_key=groq_key)
                print("[OK] Groq API configured")
            except:
                self.groq_client = None
                print("[WARNING] Groq initialization failed")
        else:
            self.groq_client = None
            print("[INFO] Groq disabled")

        # Settings
        self.enable_preprocessing = True

        # Feedback storage
        self.feedback_dir = "./handwriting_feedback"
        self.corrections_cache_dir = os.path.join(self.feedback_dir, "corrections")
        os.makedirs(self.feedback_dir, exist_ok=True)
        os.makedirs(self.corrections_cache_dir, exist_ok=True)

    # ----------------------------------------------------------------------
    # Helper utilities
    # ----------------------------------------------------------------------
    def _get_image_hash(self, image_path: str) -> str:
        """Generate hash for caching corrections."""
        with open(image_path, "rb") as f:
            return hashlib.sha256(f.read()).hexdigest()[:16]

    def _get_cached_correction(self, image_hash: str) -> Optional[Dict[str, Any]]:
        """Load existing human-corrected feedback."""
        path = os.path.join(self.corrections_cache_dir, f"{image_hash}.json")
        if os.path.exists(path):
            try:
                with open(path, "r") as f:
                    return json.load(f)
            except:
                return None
        return None

    def _save_correction(self, image_hash: str, corrected_data: Dict[str, Any], original_image_path: str):
        """Save corrected result."""
        path = os.path.join(self.corrections_cache_dir, f"{image_hash}.json")
        payload = {
            "corrected_at": time.time(),
            "original_image": os.path.basename(original_image_path),
            "data": corrected_data
        }
        with open(path, "w") as f:
            json.dump(payload, f, indent=2)

    # ----------------------------------------------------------------------
    # Image preprocessing
    # ----------------------------------------------------------------------
    def preprocess_image(self, image_path: str) -> Image.Image:
        img = Image.open(image_path)
        if img.mode != 'RGB':
            img = img.convert('RGB')

        img = ImageEnhance.Contrast(img).enhance(1.5)
        img = ImageEnhance.Sharpness(img).enhance(1.3)
        img = img.filter(ImageFilter.MedianFilter(size=3))

        # Upscale if too small
        width, height = img.size
        if max(width, height) < 512:
            scale = 512 / max(width, height)
            img = img.resize(
                (int(width * scale), int(height * scale)),
                Image.Resampling.LANCZOS
            )

        return img

    def encode_image(self, image_path: str) -> str:
        try:
            img = self.preprocess_image(image_path)
            buffer = io.BytesIO()
            img.save(buffer, format='JPEG', quality=95)
            bytes_data = buffer.getvalue()
        except Exception:
            with open(image_path, "rb") as f:
                bytes_data = f.read()

        return base64.b64encode(bytes_data).decode("utf-8")

    # ----------------------------------------------------------------------
    # Prompt
    # ----------------------------------------------------------------------
    def _build_prompt(self) -> str:
        return """
You are an expert OCR system specialized in handwritten text.

Extract ALL visible information with maximum accuracy.

Rules:
- Read carefully; do not hallucinate anything.
- Extract numbers, names, addresses, emails, dates exactly.
- If something is unclear, mark as "unreadable".
- Output ONLY clean JSON with meaningful keys.
"""

    # ----------------------------------------------------------------------
    # Main extraction pipeline
    # ----------------------------------------------------------------------
    def extract_handwriting(self, image_path: str, filename: str) -> Dict[str, Any]:

        # 1️⃣ Check feedback cache
        image_hash = self._get_image_hash(image_path)
        cached = self._get_cached_correction(image_hash)
        if cached:
            return {
                "success": True,
                "filename": filename,
                "extracted_data": cached["data"],
                "message": "Returned corrected cached result",
                "source": "cache"
            }

        # 2️⃣ Must use HuggingFace (vision)
        if not self.hf_client:
            return {
                "success": False,
                "filename": filename,
                "message": "HF_TOKEN is missing. Cannot run OCR.",
                "error": "HuggingFace token not configured"
            }

        return self.extract_handwriting_huggingface(image_path, filename)

    # ----------------------------------------------------------------------
    # HuggingFace Vision inference
    # ----------------------------------------------------------------------
    def extract_handwriting_huggingface(self, image_path: str, filename: str) -> Dict[str, Any]:
        try:
            with open(image_path, "rb") as f:
                encoded = base64.b64encode(f.read()).decode()

            prompt = self._build_prompt()

            completion = self.hf_client.chat.completions.create(
                model="Qwen/Qwen2.5-VL-7B-Instruct:hyperbolic",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{encoded}"}}
                        ]
                    }
                ]
            )

            raw_text = completion.choices[0].message.content
            structured = self._parse_json(raw_text)

            return {
                "success": True,
                "filename": filename,
                "extracted_data": structured,
                "message": "Extracted successfully",
                "source": "huggingface"
            }

        except Exception as e:
            return {
                "success": False,
                "filename": filename,
                "error": str(e),
                "message": "HuggingFace extraction failed"
            }

    # ----------------------------------------------------------------------
    # JSON parsing
    # ----------------------------------------------------------------------
    def _parse_json(self, text: str) -> Dict[str, Any]:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned[7:]
        if cleaned.startswith("```"):
            cleaned = cleaned[3:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]

        try:
            return json.loads(cleaned)
        except:
            return {"raw_text": cleaned}

    # ----------------------------------------------------------------------
    # Feedback / finetune
    # ----------------------------------------------------------------------
    def provide_feedback(self, image_path: str, filename: str, corrected_data: Dict[str, Any]):
        img_hash = self._get_image_hash(image_path)
        self._save_correction(img_hash, corrected_data, image_path)
        print(f"[OK] Feedback stored for {filename}")

    def export_finetune_dataset(self) -> List[Dict[str, Any]]:
        dataset = []
        for file in os.listdir(self.corrections_cache_dir):
            if file.endswith(".json"):
                try:
                    with open(os.path.join(self.corrections_cache_dir, file), "r") as f:
                        item = json.load(f)
                        dataset.append(item)
                except:
                    pass

        return dataset
