# Ứng dụng Android AI: Nhận diện vật thể trong nhà, Ước lượng khoảng cách & Cảnh báo giọng nói (ĐACN2)

Ứng dụng chạy trực tiếp trên thiết bị Android (**On-device Edge AI**) sử dụng mô hình **YOLOv8 TFLite** kết hợp **CameraX**, **Text-to-Speech (TTS) Tiếng Việt** và **Cảm biến phát hiện té ngã**.

---

## 🌟 Các tính năng chính

1. **Nhận diện vật thể thời gian thực (Real-time Object Detection)**:
   - Mô hình: **YOLOv8n TFLite** (80 lớp COCO).
   - Tối ưu nhận diện các vật dụng trong nhà: Bàn ghế (`chair`, `dining table`, `couch`), giường (`bed`), chai lọ (`bottle`), cốc (`cup`), tivi (`tv`), laptop, người (`person`), cửa/bồn rửa, v.v.
2. **Ước lượng khoảng cách & Hướng di chuyển**:
   - Phân loại 3 mức độ cảnh báo: **Rất gần** (Đỏ), **Khoảng cách vừa** (Cam), **Ở xa** (Xanh).
   - Xác định vị trí: **Bên trái**, **Phía trước**, **Bên phải**.
3. **Cảnh báo bằng giọng nói tiếng Việt (TTS)**:
   - Phát âm câu cảnh báo tự nhiên: *"Có cái ghế ở phía trước, rất gần"*.
   - Cơ chế throttling và lọc ưu tiên thông minh, chỉ cảnh báo vật thể cản đường gần nhất.
4. **Phát hiện té ngã (Fall Detection)**:
   - Sử dụng cảm biến gia tốc (`Accelerometer`) và con quay hồi chuyển (`Gyroscope`).
   - Tự động rung chuông và đọc cảnh báo khẩn cấp khi phát hiện người dùng ngã đột ngột.

---

## 🚀 Hướng dẫn cài đặt & Chạy dự án

### Bước 1: Xuất mô hình YOLOv8n TFLite
Cài đặt thư viện và chạy script tự động tải + xuất mô hình vào thư mục `app/src/main/assets/`:

```bash
pip install ultralytics
python export_yolov8_tflite.py
```

*Sau khi chạy, file `yolov8n.tflite` sẽ tự động được đặt trong `app/src/main/assets/yolov8n.tflite`.*

### Bước 2: Mở dự án trong Android Studio
1. Mở **Android Studio** -> Chọn **Open** -> Điều hướng đến thư mục dự án `ĐACN2`.
2. Chờ Gradle Sync hoàn tất.
3. Kết nối điện thoại Android (bật *USB Debugging*) hoặc mở máy ảo Android (Emulator hỗ trợ Camera).
4. Nhấn **Run (Shift + F10)** để cài đặt và chạy ứng dụng.

---

## 📂 Cấu trúc thư mục mã nguồn

```
ĐACN2/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Khai báo quyền Camera, Cảm biến, Rung
│   │   ├── assets/
│   │   │   ├── labels.txt               # Danh sách 80 nhãn COCO
│   │   │   └── yolov8n.tflite           # File mô hình AI TensorFlow Lite
│   │   ├── java/com/dacn2/objectdetect/
│   │   │   ├── ml/
│   │   │   │   ├── BoundingBox.kt       # Data class hộp bao, khoảng cách, hướng
│   │   │   │   ├── CocoLabels.kt        # Từ điển song ngữ Anh - Việt
│   │   │   │   └── YoloDetector.kt      # Bộ giải mã YOLOv8 TFLite, NMS
│   │   │   ├── sensors/
│   │   │   │   └── FallDetector.kt      # Thuật toán phát hiện rơi tự do & va chạm
│   │   │   ├── tts/
│   │   │   │   └── TTSManager.kt        # Quản lý TextToSpeech tiếng Việt
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt      # Điều phối CameraX, UI, sự kiện
│   │   │   │   └── OverlayView.kt       # Vẽ Bounding box màu theo khoảng cách
│   │   │   └── utils/
│   │   │       └── ImageUtils.kt        # Xử lý xoay và chuyển đổi ảnh CameraX
│   │   └── res/layout/activity_main.xml # Giao diện HUD camera, FPS, nút bật/tắt
│   └── build.gradle
├── export_yolov8_tflite.py              # Script xuất mô hình TFLite
└── settings.gradle
```
