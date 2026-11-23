# Hướng dẫn phát hành Vibe Debug (IzzyOnDroid / F-Droid / Google Play)

- **Tên ứng dụng:** Vibe Debug
- **Application ID / Package:** `io.github.xom11.vibedebug`
- **Repository:** `https://github.com/xom11/vibe-debug`
- **Giấy phép:** `GPL-3.0-or-later`
- **Privacy Policy URL:** `https://github.com/xom11/vibe-debug/blob/main/PRIVACY.md`
- **SHA-256 certificate fingerprint (`release-keystore.jks`):**
  `ec31e1dd2bbbe1d88d7bd8e3778d0323dd9e24e865ec74ab8f94da6317c1822c`

> **Lưu ý sao lưu:** Hãy giữ bản sao lưu an toàn của `release-keystore.jks` và `keystore.properties` (cả hai đều nằm trong `.gitignore`, tuyệt đối không commit lên Git). Mất khoá này sẽ không thể đẩy bản cập nhật lên cả Google Play lẫn F-Droid.

---

## 1. Cập nhật cho các phiên bản sau

Khi nâng phiên bản mới (ví dụ `2.0.1` / `versionCode = 4`):
1. Cập nhật `versionCode` & `versionName` trong `app/build.gradle.kts` và `SPEC.md`.
2. Tạo changelog tương ứng tại `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` và `fastlane/metadata/android/vi/changelogs/<versionCode>.txt` (mỗi file < 500 byte UTF-8).
3. Push lên `main` rồi đẩy tag dạng `v2.0.1`:
   ```bash
   git tag -a v2.0.1 -m "Release v2.0.1"
   git push origin v2.0.1
   ```
   Workflow `.github/workflows/release.yml` sẽ tự build và phát hành `vibe-debug-2.0.1.apk` + `SHA256SUMS.txt` lên **GitHub Releases**, đồng thời lưu `vibe-debug-2.0.1.aab` ở tab **Actions → Release → Artifacts**.

---

## 2. Đăng lên IzzyOnDroid (duyệt nhanh ~1–2 ngày)

1. Mở issue mới tại: `https://gitlab.com/IzzyOnDroid/repo/-/issues`
2. Nội dung gợi ý để copy-paste:
   - **App Name:** Vibe Debug
   - **Package ID:** `io.github.xom11.vibedebug`
   - **Repo URL:** `https://github.com/xom11/vibe-debug`
   - **License:** `GPL-3.0-or-later`
   - **Release APK:** GitHub Releases (`vibe-debug-%v.apk`, ~1.3 MB)
   - **Fastlane metadata:** Có sẵn trong `fastlane/metadata/android/` (`en-US` và `vi`, kèm `icon.png` 512×512 và `featureGraphic.png` 1024×500)
   - **Reproducible build:** Tắt `dependenciesInfo` & `vcsInfo`, ghim `distributionSha256Sum` trong `gradle-wrapper.properties`.

---

## 3. Đăng lên F-Droid chính thức (`fdroiddata`)

1. Fork repo `https://gitlab.com/fdroid/fdroiddata`.
2. Copy trực tiếp file `fastlane/io.github.xom11.vibedebug.yml` trong repo này sang `metadata/io.github.xom11.vibedebug.yml` của `fdroiddata` (đã điền sẵn `Repo`, `Binaries`, `Builds`, và `AllowedAPKSigningKeys`).
3. Commit và mở **Merge Request** lên `fdroid/fdroiddata`.

---

## 4. Nộp lên Google Play Console (Closed Testing → Production)

- **Bản `.aab` đã ký:** `app/build/outputs/bundle/release/app-release.aab` (hoặc tải `play-store-aab-2.0.0` từ GitHub Actions Artifacts).
- **Target API:** `compileSdk = 36`, `targetSdk = 36` (Android 16) với adaptive layout (`AdaptiveLayout.kt`) cho điện thoại, máy tính bảng và màn hình gập.
- **Đồ hoạ Store:**
  - App Icon (`512×512`): `fastlane/metadata/android/en-US/images/icon.png`
  - Feature Graphic (`1024×500`): `fastlane/metadata/android/en-US/images/featureGraphic.png`
  - Nội dung Store song ngữ: `fastlane/metadata/android/{en-US,vi}/`
  -*(Nên chụp thêm 2–6 screenshot từ máy thật vào `fastlane/metadata/android/en-US/images/phoneScreenshots/` để dùng chung cho Play Store và F-Droid.)*
- **Privacy Policy URL:** `https://github.com/xom11/vibe-debug/blob/main/PRIVACY.md`
- **App Content & Data Safety:**
  - **Data collected / shared:** **No** (không khai báo `INTERNET`, không thu thập và không chia sẻ dữ liệu).
  - **App access:** *"All functionality is available without special access"* (không có tài khoản đăng nhập).
  - **Ads:** **No**.
  - **Closed testing:** Đủ tối thiểu **12 tester** opt-in & sử dụng liên tục trong **14 ngày** trước khi xin mở Production (áp dụng với tài khoản cá nhân tạo sau 13/11/2023).
  - **Giải trình `WRITE_SECURE_SETTINGS` nếu reviewer hỏi:** Mặc định (Mode A) ứng dụng hoạt động đầy đủ không cần quyền đặc biệt (đọc trạng thái trực tiếp, hướng dẫn theo từng hãng, deep-link tới đúng công tắc trong Cài đặt, 4 Quick Settings tiles và 4 Widget 1×1). Quyền `WRITE_SECURE_SETTINGS` chỉ mở khoá tuỳ chọn 1 chạm khi chính người dùng tự cấp qua ADB/Shizuku (tương tự *SystemUI Tuner*).
