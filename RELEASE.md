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
     JDK 21 and Android SDK platform 37.1 (compileSdk = 37.1, targetSdk = 36); Gradle 9 wrapper (checksum pinned), AGP 9.
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

### 4.1. Bản `.aab` để tải lên

- Luôn lấy AAB **build từ đúng tag phát hành** (`vX.Y.Z`), không build từ `main`: tải artifact `play-store-aab-<version>` ở **Actions → Release**, hoặc build lại tại tag:
  ```bash
  git checkout vX.Y.Z && ./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
  ```
  Như vậy bản Play, bản GitHub Release và bản F-Droid cùng `versionCode` sẽ cùng mã nguồn.
- **Target API:** `targetSdk = 36` (Android 16), đạt yêu cầu target API của Play (`compileSdk = 36` tới v2.2.4, `37.1` từ bản sau). Native lib duy nhất (`libandroidx.graphics.path.so`) đã align 16 KB.
- ⚠️ Từ sau v2.2.4, `main` đã nâng AGP 9 / Kotlin 2.4 / Compose BOM 2026.11: trước khi tag bản kế tiếp, bump `versionCode`/`versionName` và test UI trên máy thật (CI emulator suite + thiết bị OEM).
- AAB đã kèm sẵn R8 mapping (`BUNDLE-METADATA/.../proguard.map`), không cần upload mapping riêng.

### 4.2. Play App Signing — ⚠️ quyết định một lần, không đổi lại được

App đã phát hành trên GitHub / Obtainium / IzzyOnDroid (và F-Droid reproducible build) bằng khoá `ec31e1dd…`. Nếu để Google **tự tạo** app signing key, bản cài từ Play sẽ khác chữ ký → người dùng không cập nhật chéo được giữa Play và các nguồn khác (phải gỡ app, mất cấu hình).

Khi tạo release đầu tiên:
1. Ở bước **App integrity → App signing**, chọn **Use a different key / Change app signing key** → **Export and upload a key from Java keystore**.
2. Tải `pepk.jar` và `encryption_public_key.pem` từ chính trang đó, rồi chạy **đúng lệnh Play Console hiển thị**, dạng:
   ```bash
   java -jar pepk.jar --keystore=release-keystore.jks --alias=<keyAlias> \
     --output=vibedebug-signing-key.zip --include-cert --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```
3. Upload `vibedebug-signing-key.zip`. Upload key: dùng chính `release-keystore.jks` (workflow `release.yml` đang ký AAB bằng khoá này, không cần đổi gì).
4. Sau khi lưu, kiểm tra **App integrity → App signing key certificate → SHA-256** phải đúng `EC:31:E1:DD:…:C1:82:2C`.

> ✅ Đã làm ngày 2026-11-24: app signing key và upload key trên Play Console đều là `EC:31:E1:DD:…:C1:82:2C` (khoá Google tự tạo trước đó `18:69:0B:1B:…:58:FF` đã bị thay trước khi có bản nào được phân phối).

### 4.3. Store listing

- **Nội dung song ngữ:** copy từ `fastlane/metadata/android/{en-US,vi}/` (`title.txt` ≤ 30, `short_description.txt` ≤ 80, `full_description.txt` ≤ 4000 ký tự).
- **Đồ hoạ:**
  - App icon `512×512`: `fastlane/metadata/android/en-US/images/icon.png`
  - Feature graphic `1024×500` (PNG 24-bit, không alpha): `fastlane/metadata/android/en-US/images/featureGraphic.png`
  - Phone screenshots `1200×2400` (không alpha): `fastlane/metadata/android/{en-US,vi}/images/phoneScreenshots/`
  - Tablet 7" & 10" `1920×1080`: `fastlane/metadata/android/en-US/images/{sevenInch,tenInch}Screenshots/`
  - Play Console ghi khuyến nghị 16:9 / 9:16, nhưng thực tế đã nhận bộ ảnh gốc 1080×2400 (20:9) hiện đang nằm trên listing; bản 1200×2400 trong repo chỉ là phương án an toàn hơn, không bắt buộc thay.
- **Category:** Tools. **Website:** `https://github.com/xom11/vibe-debug`. **Contact email:** bắt buộc, sẽ hiển thị công khai trên Play.

### 4.4. App content (Policy → App content)

| Mục | Khai báo |
|---|---|
| Privacy policy | `https://github.com/xom11/vibe-debug/blob/main/PRIVACY.md` |
| Ads | **No** |
| Thông tin đăng nhập (trước đây là *App access*) | **Có** (mục "thao tác cần thực hiện trên một thiết bị khác") → *Thêm chi tiết*: tên `1-Tap Mode setup (no account, ADB grant)`, để trống username/password, dán hướng dẫn ở mục 4.5 vào ô *Mọi thông tin cần thiết khác* |
| Content rating (IARC) | Category *Utility / Productivity / Other*, trả lời **No** cho mọi câu |
| Target audience | **18+** (công cụ cho nhà phát triển; tránh nhóm dưới 13 để không thuộc chính sách Families) |
| Data safety | **No** data collected or shared (không có `INTERNET`; mọi xử lý nằm trên máy) |
| Advertising ID | **No** (manifest không có `AD_ID`) |
| Financial features | *My app doesn't provide any financial features* |
| Government apps / Health / News | **No** |
| Foreground service permissions | Khai báo `FOREGROUND_SERVICE_SPECIAL_USE` → mục 4.6 |

### 4.5. Hướng dẫn cho reviewer (Thông tin đăng nhập → Mọi thông tin cần thiết khác, tối đa 500 ký tự)

```text
No account or login. Core features work with no setup. 1-Tap Mode and "Auto-turn off when opening apps" need WRITE_SECURE_SETTINGS, which can only be granted from a computer: enable USB debugging, connect ADB, run
adb shell pm grant io.github.xom11.vibedebug android.permission.WRITE_SECURE_SETTINGS
and reopen the app. Auto turn-off: Shortcuts tab > Choose apps > pick any app > enable "Auto-turn off when opening apps" > allow Usage Access > open that app from its icon.
```

### 4.6. Foreground service declaration (`specialUse`)

Service `AppWatcherService` chỉ chạy khi người dùng đã cấp `WRITE_SECURE_SETTINGS` qua ADB **và** Usage Access, nên reviewer khó tự tái hiện → **video là bắt buộc thực tế**, quay đủ luồng.

Form trên Play Console: tích **Khác** → ô *Đường liên kết đến video* (bắt buộc, nút Lưu bị khoá nếu trống) và ô *Mô tả việc sử dụng quyền*.

**Mô tả (dán vào ô *Mô tả việc sử dụng quyền*):**
```text
Vibe Debug lets users pick apps (e.g. banking or digital ID apps) that refuse to run while Developer Options or USB debugging is on. Only when the user explicitly enables "Auto-turn off when opening apps" (off by default; needs the user-granted WRITE_SECURE_SETTINGS permission and Usage Access, shown with an in-app disclosure first) does the app start this foreground service. While the screen is on it checks the current foreground app on-device with UsageStatsManager, turns debugging off the moment a selected app opens from its normal launcher icon, and restores it a few seconds after the user leaves. It must start immediately and keep running because the selected apps check Developer Options at launch: a deferred or paused job (WorkManager/JobScheduler) would react too late and the app would refuse to open. The service pauses itself while the screen is off, shows an ongoing notification with a Stop action, and stops when the feature is turned off. No data leaves the device (no INTERNET permission) and AccessibilityService is not used.
```

**Kịch bản video (30–60 giây, quay màn hình máy thật, upload YouTube dạng Unlisted):**
1. Mở Vibe Debug → banner *1-Tap Mode active*, USB debugging đang **BẬT**.
2. Tab **Shortcuts** → **Choose apps** → chọn 1 app (ví dụ app ngân hàng) → bật **Auto-turn off when opening apps** → hộp thoại giải thích Usage Access → cấp quyền.
3. Kéo thanh thông báo: thông báo cố định *Watching 1 protected app* kèm nút **Stop**.
4. Về màn hình chính, mở app đã chọn từ icon gốc → app mở bình thường; quay lại Cài đặt nhanh / Vibe Debug cho thấy gỡ lỗi đã **TẮT**.
5. Thoát app đã chọn → sau ~8 giây gỡ lỗi tự **BẬT** lại.
6. Bấm **Stop** trên thông báo → thông báo biến mất, tính năng tắt.

Nếu bị từ chối: phương án dự phòng là build riêng cho Play không có `AppWatcherService` (giữ lối tắt Clean Launch thủ công).

> ✅ Đã nộp ngày 2026-11-25: video demo (OnePlus 12R, Android 16, 64 giây, đã làm mờ dữ liệu cá nhân, có chú thích tiếng Anh) ở chế độ **Unlisted**: <https://www.youtube.com/watch?v=YaoZqcMVnXE>. Bản v2.2.4 (`versionCode 9`) trên kênh *Thử nghiệm khép kín – Alpha* (178 quốc gia) cùng trang thông tin cửa hàng và các khai báo đã được gửi Google xem xét, và **được duyệt, xuất bản cùng ngày**.
>
> ✅ Tester (2026-11-25): kênh Alpha dùng Google Group `apphive-testers@googlegroups.com` (chung nhóm AppHive với app khác của tài khoản), kênh phản hồi là <https://github.com/xom11/vibe-debug/issues>; thay đổi đã được gửi xem xét. Link tham gia: Android <https://play.google.com/store/apps/details?id=io.github.xom11.vibedebug>, web <https://play.google.com/apps/testing/io.github.xom11.vibedebug>.

### 4.7. Closed testing → Production

- Tài khoản cá nhân tạo sau 13/11/2023: cần **≥ 12 tester** opt-in và dùng liên tục **14 ngày** trong closed testing trước khi xin quyền Production.
- **Giải trình `WRITE_SECURE_SETTINGS` nếu reviewer hỏi:** Mặc định (Mode A) ứng dụng hoạt động đầy đủ không cần quyền đặc biệt (đọc trạng thái trực tiếp, hướng dẫn theo từng hãng, deep-link tới đúng công tắc trong Cài đặt, 2 ô Cài đặt nhanh trọng tâm và 1 Widget USB & Wi-Fi tự co giãn ngoài màn hình chính). Quyền `WRITE_SECURE_SETTINGS` chỉ mở khoá tuỳ chọn 1 chạm khi chính người dùng tự cấp qua ADB/Shizuku (tương tự *SystemUI Tuner*).
