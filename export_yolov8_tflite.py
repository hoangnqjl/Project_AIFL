"""
Script tự động tải và xuất mô hình YOLOv8n sang định dạng TensorFlow Lite (TFLite)
dành cho ứng dụng Android.

Yêu cầu:
    pip install ultralytics

Sử dụng:
    python export_yolov8_tflite.py
"""

import os
import shutil
import sys

def export_model():
    try:
        from ultralytics import YOLO
    except ImportError:
        print("[!] Ultralytics not found. Installing...")
        os.system(f"{sys.executable} -m pip install ultralytics")
        from ultralytics import YOLO

    print("[*] Loading YOLOv8n pre-trained model (COCO 80 classes)...")
    model = YOLO("yolov8n.pt")

    print("[*] Exporting model to TFLite format (640x640)...")
    exported_path = model.export(format="tflite", imgsz=640)
    print(f"[+] Export result: {exported_path}")

    target_dir = os.path.join("app", "src", "main", "assets")
    os.makedirs(target_dir, exist_ok=True)
    target_file = os.path.join(target_dir, "yolov8n.tflite")

    source_file = None
    if os.path.isfile("yolov8n.tflite"):
        source_file = "yolov8n.tflite"
    elif os.path.isdir("yolov8n_saved_model"):
        for f in os.listdir("yolov8n_saved_model"):
            if f.endswith(".tflite"):
                source_file = os.path.join("yolov8n_saved_model", f)
                break

    if source_file and os.path.isfile(source_file):
        shutil.copyfile(source_file, target_file)
        print(f"[SUCCESS] Copied model to: {target_file}")
    else:
        print(f"[!] Warning: Please copy the exported .tflite file into '{target_file}'")

if __name__ == "__main__":
    export_model()

