# Auto Xếp Gạch Shopee (Android Kotlin Project)

Ứng dụng Android tự động nhận diện bảng 8x8 và 3 khối gạch trên màn hình (`MediaProjection`), tính nước đi tối ưu (AI Beam Search trên Bitboard 64-bit), và tự động kéo thả khối gạch (`AccessibilityService`).

## Cách xuất ra file `.apk` (2 cách)

### Cách 1: Không cần máy tính / Không cần cài Android Studio (Dùng GitHub Actions miễn phí)
1. Tạo tài khoản miễn phí trên [github.com](https://github.com) và tạo một **Repository** mới.
2. Giải nén file ZIP và tải toàn bộ nội dung bên trong thư mục `ShopeeBlockAutoBot` (gồm `.github`, `app`, `gradle`, `build.gradle.kts`, `settings.gradle.kts`...) lên Repository đó.
3. Vào tab **Actions** trên GitHub -> chọn workflow **Build Debug APK** -> bấm **Run workflow** (hoặc chờ tự chạy sau khi upload).
4. Sau 2–3 phút, mở lượt chạy xanh -> kéo xuống mục **Artifacts** -> tải **`ShopeeBlockAutoBot-debug-apk`** về điện thoại và cài file `app-debug.apk`.

### Cách 2: Biên dịch trực tiếp bằng Android Studio (trên PC/Laptop)
1. Cài đặt **Android Studio** (JDK 17+).
2. Mở thư mục `ShopeeBlockAutoBot` bằng **Open Project**.
3. Chờ Gradle Sync xong -> chọn menu **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. Lấy file APK tại: `app/build/outputs/apk/debug/app-debug.apk`.
