import os
import io
from PIL import Image
from google.cloud import vision
from config import GOOGLE_APPLICATION_CREDENTIALS

# Set credentials path
if GOOGLE_APPLICATION_CREDENTIALS:
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = GOOGLE_APPLICATION_CREDENTIALS


def compress_image(image_path, max_size=1600):
    """Compress and resize image if needed for better OCR performance.
    Phone cameras produce 4000x3000+ images which are unnecessarily large for text detection."""
    img = Image.open(image_path)

    # Convert RGBA/palette to RGB
    if img.mode != 'RGB':
        img = img.convert('RGB')

    # Resize if larger than max_size on any dimension
    w, h = img.size
    if max(w, h) > max_size:
        ratio = max_size / max(w, h)
        new_w = int(w * ratio)
        new_h = int(h * ratio)
        img = img.resize((new_w, new_h), Image.LANCZOS)

    # Save to bytes
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=85)
    return buf.getvalue()


def extract_text_from_image(image_path):
    """Extract text from an image file using Google Cloud Vision API."""
    try:
        client = vision.ImageAnnotatorClient()

        # Compress image for better performance
        content = compress_image(image_path)

        image = vision.Image(content=content)
        response = client.text_detection(image=image)

        if response.error.message:
            raise Exception(f"Vision API error: {response.error.message}")

        texts = response.text_annotations
        if texts:
            return texts[0].description  # Full extracted text
        return ""
    except Exception as e:
        print(f"OCR Error: {e}")
        raise
