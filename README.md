# IELTS Vocab 7.0 — Ứng dụng học từ vựng IELTS Academic (Band 5.5 → 7.0+)

Ứng dụng Android học từ vựng **IELTS Academic** dành cho người học Việt Nam hướng tới mục tiêu nâng band từ **5.5 lên 7.0+**, được tối ưu hóa cho **Samsung Galaxy A55** và mọi kích thước màn hình Android.

---

## 1. Kiến trúc & Ngăn xếp Công nghệ (Technology Stack)

Kho mã nguồn hiện tại là dự án **Native Android (Kotlin + Jetpack Compose + Room SQLite + Gradle)**. Theo nguyên tắc giữ lại ngăn xếp tương thích của kho mã nguồn hiện có để đảm bảo hiệu năng cao nhất trên thiết bị vật lý **Samsung Galaxy A55** và môi trường build Android:

- **Ngôn ngữ & UI**: Kotlin 2.2 + Jetpack Compose (Material Design 3, hỗ trợ Light/Dark mode, Edge-to-Edge `WindowInsets`, bố cục thích ứng `NavigationBar` / `NavigationRail`).
- **Cơ sở dữ liệu cục bộ**: **Room SQLite** (`IeltsDatabase` v2 kèm `MIGRATION_1_2`) với đầy đủ 10 bảng:
  - `vocabulary_entries` (chỉ mục duy nhất trên `normalized_headword`, chống trùng lặp bản ghi gốc)
  - `topics` (15 chủ đề IELTS Academic)
  - `vocabulary_topic_cross_ref` (quan hệ nhiều-nhiều giữa từ vựng và chủ đề)
  - `user_vocabulary_progress` (trạng thái Spaced Repetition: `NEW`, `LEARNING`, `REVIEW`, `MASTERED`)
  - `review_history` (lịch sử từng lần lật thẻ/trả lời câu hỏi)
  - `quiz_questions` & `quiz_attempts` (câu hỏi đã xác thực và lịch sử điểm/thời gian làm bài)
  - `review_sessions` (lưu bền vững trạng thái **Mandatory Review Gate** và phiên Flashcard đang học dở khi đóng/mở lại ứng dụng)
  - `user_settings` (mục tiêu ngày, giờ nhắc học, chu kỳ ôn tập, chế độ sáng/tối)
  - `llm_response_cache` (bộ nhớ đệm SQLite cho các phản hồi từ LLM)
- **Tích hợp AI (Gemini & Provider-Agnostic LLM)**:
  - **`gemini-4-argon` (`ThinkingLevel.HIGH`)**: Phân tích chuyên sâu sự khác biệt sắc thái nghĩa (nuance) và cách dùng Band 7.0+ giữa các từ học thuật gần nghĩa.
  - **`gemini-3.8-flash` (Structured JSON + Google Search Grounding)**: Bổ sung collocations, họ từ, lỗi thường gặp, sinh biến thể câu hỏi trắc nghiệm có kiểm tra hợp lệ (`QuizEngine.validateLlmQuestion`) và tra cứu luận điểm IELTS thực tế qua công cụ `googleSearch`.
  - **`gemini-3.1-flash-lite`**: Tạo mẹo ghi nhớ nhanh (mnemonic) tức thì trên thẻ Flashcard.
  - Hoạt động **100% ngoại tuyến** khi chưa cấu hình khóa API.

---

## 2. Nguồn dữ liệu từ vựng & Lộ trình đạt 1.200 từ xác thực

- **Chính sách nguồn dữ liệu**:
  - Dựa trên nền tảng **Academic Word List (AWL)** của GS. Averil Coxhead (Victoria University of Wellington) và **English Vocabulary Profile**, kết hợp từ vựng học thuật theo 15 chủ đề IELTS Academic.
  - Không tự bịa đặt URL nguồn hay mức CEFR: các trường chưa xác thực được giữ giá trị `null`.
  - Lưu ý học thuật: **Mức CEFR (B2/C1/C2) chỉ mang tính tham khảo và không tương đương trực tiếp với điểm IELTS Band**.
- **Trạng thái bộ dữ liệu khởi đầu (`app/src/main/assets/seed_vocabulary.json`)**:
  - Hiện cung cấp **30 mục từ vựng học thuật khởi đầu** đã được kiểm duyệt kỹ lưỡng, phủ khắp toàn bộ **15 chủ đề IELTS Academic** (mỗi từ có thể thuộc nhiều chủ đề nhưng chỉ tính 1 lần duy nhất trong bảng gốc `vocabulary_entries`).
- **Kiểm tra hợp lệ & Công việc còn lại để đạt 1.200 từ**:
  - Chạy script kiểm tra độc lập cho JSON hoặc CSV:
    ```bash
    python3 scripts/validate_seed_data.py app/src/main/assets/seed_vocabulary.json
    ```
  - Người dùng hoặc nhóm biên tập nội dung có thể nhập thêm các lô từ vựng từ CSV hoặc JSON trực tiếp trong màn hình **Cài đặt → Nhập dữ liệu từ vựng (CSV / JSON Pipeline)** hoặc bổ sung vào `seed_vocabulary.json` để mở rộng từ 30 từ khởi đầu lên đủ **1.200 từ xác thực (80 từ/chủ đề)**.

---

## 3. Hướng dẫn thiết lập khóa API (`.env.example` & AI Studio Secrets)

1. Xem tệp `.env.example` ở thư mục gốc:
   ```properties
   GEMINI_API_KEY=MY_GEMINI_API_KEY
   LLM_PROVIDER=gemini
   LLM_API_KEY=MY_LLM_API_KEY
   LLM_MODEL=gemini-3.8-flash
   LLM_BASE_URL=https://generativelanguage.googleapis.com/
   ```
2. Trong **Google AI Studio**, mở bảng **Secrets** và thêm `GEMINI_API_KEY` (hoặc `LLM_API_KEY` nếu dùng nhà cung cấp tương thích khác).
3. Nếu không cấu hình khóa API, toàn bộ tính năng cốt lõi (Thư viện 15 chủ đề, Flashcard, 3 dạng Quiz, Spaced Repetition, Mandatory Review Gate, Thống kê và Thông báo cục bộ) vẫn hoạt động hoàn chỉnh.

---

## 4. Chạy trên Máy ảo Android, Samsung Galaxy A55 & Đóng gói APK

### A. Chạy kiểm thử tự động (Unit Tests & Robolectric Integration Tests)
```bash
gradle :app:testDebugUnitTest
```

### B. Đóng gói file cài đặt Android APK (`assembleDebug`)
```bash
gradle :app:assembleDebug
```
File APK đầu ra nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

### C. Cài đặt và chạy trên Samsung Galaxy A55
1. Trên điện thoại **Samsung Galaxy A55**, mở **Cài đặt (Settings) → Thông tin điện thoại (About phone) → Thông tin phần mềm (Software information)** và chạm 7 lần vào **Số hiệu bản tạo (Build number)** để bật **Tùy chọn nhà phát triển (Developer options)**.
2. Bật **Gỡ lỗi USB (USB debugging)** trong Tùy chọn nhà phát triển, kết nối cáp USB-C với máy tính và cài đặt APK, hoặc tải trực tiếp file APK xuất từ menu Settings của AI Studio về máy Samsung Galaxy A55 để cài đặt.
3. Để kiểm tra tính năng **Ôn tập bắt buộc (Mandatory Review Gate)** ngay trên điện thoại mà không cần chờ qua ngày hôm sau:
   - Học một vài từ trong **Flashcard** (nhấn *Hiểu rõ*) hoặc làm một bài **Quiz**.
   - Vào tab **Cài đặt → Chu kỳ Spaced Repetition & Kiểm thử ngày ôn → Nhấn nút `+1 Ngày`**.
   - Ngay lập tức màn hình **Ôn tập bắt buộc** sẽ khóa các màn hình học khác cho đến khi bạn hoàn thành bài kiểm tra các từ đến hạn.
