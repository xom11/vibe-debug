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

## 2. Đăng lên IzzyOnDroid (Codeberg — duyệt nhanh ~1–2 ngày)

IzzyOnDroid hiện tiếp nhận yêu cầu mới trên **Codeberg** (đã chuyển từ GitLab sang Codeberg):
1. Mở link tạo issue sẵn form tại:
   `https://codeberg.org/IzzyOnDroid/repodata/issues/new?template=.forgejo%2Fissue_template%2Fapp-inclusion-request.yaml`
2. Điền đúng các trường theo bảng chuẩn dưới đây (đã khớp 100% template của `IzzyOnDroid/repodata`):
   - **Title:** `[AppRequest] Vibe Debug`
   - **Guidelines:** Tích cả 4 ô (tôi là developer, tuân thủ policy, chưa có trong repo, đã có thư mục `fastlane/` kèm 8 screenshots & graphics).
   - **Link to the source code:** `https://github.com/xom11/vibe-debug`
   - **License used:** `GPL-3.0-or-later`
   - **Categories:** Chọn `Development` và `System`
   - **Summary:**
     `Inspect and switch USB & Wireless Debugging in 1 tap with auto-off timer`
   - **Description:** Copy nguyên văn `fastlane/metadata/android/en-US/full_description.txt`
   - **Build instructions:**
     ```text
     JDK 21 and Android SDK 36 (compileSdk = 36, targetSdk = 36).
     Build unsigned reproducible release APK from repo root:
       ./gradlew assembleRelease -PfdroidBuild=true
     The output APK matches the signed GitHub Release asset (`vibe-debug-%v.apk`, SHA-256 signing key `ec31e1dd2bbbe1d88d7bd8e3778d0323dd9e24e865ec74ab8f94da6317c1822c`) bit-for-bit (`dependenciesInfo` and `vcsInfo` disabled, Gradle wrapper checksum pinned).
     ```
   - **AI Tools Usage (khai báo minh bạch để qua vòng `needs/llm-check`):**
     - *Assistance Level:* `Substantial – Used throughout development` (hoặc `Moderate` theo mức bạn muốn khai)
     - *AI Tool(s):* `Antigravity / Gemini / Claude`
     - *What did the tools help with, and how?:* `Assisted with Jetpack Compose adaptive UI boilerplate, bilingual en-US/vi strings, Robolectric unit test scaffolding, and GitHub Actions/F-Droid reproducible build configuration.`
     - *AI Accountability:* Tích cả 2 ô xác nhận developer đã review/edit code và tự kiểm thử thực tế trên thiết bị.

---

## 3. Đăng lên F-Droid chính thức (`fdroiddata` / RFP)

- **Cách nhanh (Merge Request trực tiếp):**
  1. Fork repo `https://gitlab.com/fdroid/fdroiddata`.
  2. Copy trực tiếp file `fastlane/io.github.xom11.vibedebug.yml` trong repo này sang `metadata/io.github.xom11.vibedebug.yml` của `fdroiddata` (đã điền sẵn `Repo`, `Binaries`, `Builds`, và `AllowedAPKSigningKeys`).
  3. Commit và mở **Merge Request** lên `fdroid/fdroiddata`.
- **Hoặc mở Request for Packaging (RFP) nếu muốn maintainer xem trước:**
  `https://gitlab.com/fdroid/rfp/-/issues/new` (Application ID: `io.github.xom11.vibedebug`).

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
  - **Giải trình `WRITE_SECURE_SETTINGS` nếu reviewer hỏi:** Mặc định (Mode A) ứng dụng hoạt động đầy đủ không cần quyền đặc biệt (đọc trạng thái trực tiếp, hướng dẫn theo từng hãng, deep-link tới đúng công tắc trong Cài đặt, 2 ô Cài đặt nhanh trọng tâm và 1 Widget USB & Wi-Fi tự co giãn ngoài màn hình chính). Quyền `WRITE_SECURE_SETTINGS` chỉ mở khoá tuỳ chọn 1 chạm khi chính người dùng tự cấp qua ADB/Shizuku (tương tự *SystemUI Tuner*).
