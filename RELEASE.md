# Hướng dẫn bàn giao & phát hành (GitHub Public / IzzyOnDroid / F-Droid / Google Play)

Tài liệu này gom toàn bộ bước cuối để bạn triển khai ngay sau khi hoàn tất việc **đổi tên app (ví dụ: Vibe Debug), đổi `applicationId` và chuyển repo sang org `xom11`** ở session khác.

---

## 1. Checklist trước khi mở Public & gắn tag `v2.0.0`

Khi bạn đổi tên app, `applicationId` và tên repo trong org `xom11`, hãy rà soát cập nhật đồng bộ ở các file sau:

- [ ] `app/build.gradle.kts` (`namespace`, `applicationId`, và kiểm tra `versionName` / `versionCode`)
- [ ] `app/src/main/AndroidManifest.xml` (custom actions `ACTION_SAFE_LAUNCH`, `ACTION_WIDGET_TOGGLE*` nếu muốn đồng bộ package mới)
- [ ] `app/src/main/res/xml/shortcuts.xml` (`targetPackage`, `targetClass`, action)
- [ ] `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml` (`app_name`)
- [ ] `app/src/main/java/.../navigation/SettingsIntents.kt` (`ADB_GRANT_COMMAND`, `ADB_REINSTALL_GRANT_COMMAND`)
- [ ] `app/src/main/java/.../shortcut/SafeLaunchHelper.kt`, `ShortcutDispatchActivity.kt`, `widget/UsbDebugWidgetProvider.kt`
- [ ] `app/proguard-rules.pro` (nếu đổi tên package Kotlin)
- [ ] `fastlane/metadata/android/en-US/{title.txt,full_description.txt}` và `fastlane/metadata/android/vi/{title.txt,full_description.txt}`
- [ ] `PRIVACY.md`, `README.md`, `SPEC.md`, `fastlane/fdroid-recipe-template.yml`

> **Lưu ý về chữ ký:** Keystore phát hành đã được tạo sẵn tại `release-keystore.jks` cùng `keystore.properties` (cả hai đều nằm trong `.gitignore`, **tuyệt đối không commit**). Hãy sao lưu `release-keystore.jks` và `keystore.properties` ra trình quản lý mật khẩu / ổ sao lưu an toàn — **Google Play & F-Droid đều bắt buộc giữ nguyên khoá này cho mọi bản cập nhật về sau**.
>
> SHA-256 certificate fingerprint của khoá hiện tại:
> `ec31e1dd2bbbe1d88d7bd8e3778d0323dd9e24e865ec74ab8f94da6317c1822c`

---

## 2. Chuyển repo sang org `xom11`, nạp GitHub Secrets, mở Public & Release

Sau khi repo đã đổi tên/code xong trên nhánh `main`:

```bash
# 1) (Nếu dùng API transfer sang org xom11) hoặc transfer trực tiếp trên GitHub Settings
gh api -X POST repos/l3n4k4/usb-debug-switch/transfer -f new_owner=xom11 -f new_name=<NEW_REPO_SLUG>
git remote set-url origin https://github.com/xom11/<NEW_REPO_SLUG>.git

# 2) Nạp 4 GitHub Actions Secrets để workflow release.yml tự build & ký APK + AAB
base64 -i release-keystore.jks | tr -d '\n' | gh secret set SIGNING_KEY_BASE64 -R xom11/<NEW_REPO_SLUG>
grep '^storePassword=' keystore.properties | cut -d= -f2- | gh secret set SIGNING_STORE_PASSWORD -R xom11/<NEW_REPO_SLUG>
grep '^keyAlias=' keystore.properties | cut -d= -f2- | gh secret set SIGNING_KEY_ALIAS -R xom11/<NEW_REPO_SLUG>
grep '^keyPassword=' keystore.properties | cut -d= -f2- | gh secret set SIGNING_KEY_PASSWORD -R xom11/<NEW_REPO_SLUG>

# 3) Chuyển visibility sang Public (chỉ chạy khi bạn đã sẵn sàng công khai)
gh repo edit xom11/<NEW_REPO_SLUG> --visibility public --accept-visibility-change-consequences

# 4) Đẩy tag phát hành (kích hoạt GitHub Actions Release tạo GitHub Release + app-2.0.0.apk + artifact .aab)
git tag -a v2.0.0 -m "Release v2.0.0"
git push origin v2.0.0
```

Khi workflow `Release` chạy xong:
- **GitHub Releases** sẽ có file `app-2.0.0.apk` đã ký + `SHA256SUMS.txt` (người dùng dùng **Obtainium** dán URL repo là cập nhật được ngay).
- Tab **Actions → Release → Artifacts** sẽ có `play-store-aab-2.0.0` chứa `app-2.0.0.aab` đã ký để nộp lên Google Play Console.

---

## 3. Đăng lên IzzyOnDroid (duyệt nhanh ~1–2 ngày)

1. Mở issue mới tại: `https://gitlab.com/IzzyOnDroid/repo/-/issues`
2. Điền thông tin:
   - **Repo URL:** `https://github.com/xom11/<NEW_REPO_SLUG>`
   - **License:** `GPL-3.0-or-later`
   - **Application ID:** `<NEW_APPLICATION_ID>`
   - **Release APK:** GitHub Releases (`app-%v.apk`)
   - **Fastlane metadata:** Đã có sẵn trong `fastlane/metadata/android/` (gồm icon `512×512`, feature graphic `1024×500`, mô tả `en-US` & `vi`).
   - **Reproducible build:** Repo đã tắt `dependenciesInfo` & `vcsInfo` và ghim `distributionSha256Sum` trong `gradle-wrapper.properties`.

*(Bạn cũng có thể chụp thêm screenshot từ thiết bị thật hoặc Android Emulator rồi đặt vào `fastlane/metadata/android/en-US/images/phoneScreenshots/` trước khi gửi IzzyOnDroid / F-Droid để trang listing đẹp hơn.)*

---

## 4. Đăng lên F-Droid chính thức (`fdroiddata`)

1. Fork repo `https://gitlab.com/fdroid/fdroiddata`.
2. Copy mẫu tại `fastlane/fdroid-recipe-template.yml` sang `metadata/<NEW_APPLICATION_ID>.yml` trong repo `fdroiddata` đã fork, thay `<REPO_SLUG>`, `<APP_NAME>` (và đảm bảo `AllowedAPKSigningKeys` giữ đúng SHA-256 ở trên).
3. Push nhánh và mở **Merge Request** lên `fdroid/fdroiddata`.
4. CI của F-Droid sẽ tự build từ commit tag `v2.0.0` bằng `./gradlew assembleRelease` (tự động bỏ qua signing nhờ cấu hình optional trong `app/build.gradle.kts`) và đối chiếu bit-for-bit với `app-2.0.0.apk` trên GitHub Releases.

---

## 5. Nộp lên Google Play Console (Closed Testing -> Production)

Repo đã sẵn sàng các yếu tố kỹ thuật cho CH Play:
- `compileSdk = 36`, `targetSdk = 36` (Android 16), adaptive layout cho tablet/foldable (`AdaptiveLayout.kt`).
- Bản build `.aab` đã ký: tại `app/build/outputs/bundle/release/app-release.aab` (hoặc tải từ GitHub Actions Artifact `play-store-aab-2.0.0`).
- Đồ hoạ Store:
  - App Icon (`512×512`): `fastlane/metadata/android/en-US/images/icon.png`
  - Feature Graphic (`1024×500`): `fastlane/metadata/android/en-US/images/featureGraphic.png`
  - Nội dung Store song ngữ: `fastlane/metadata/android/{en-US,vi}/`
- **Privacy Policy URL:** Dùng link raw hoặc trang file `PRIVACY.md` trên GitHub sau khi mở Public (`https://github.com/xom11/<NEW_REPO_SLUG>/blob/main/PRIVACY.md`).
- **Gợi ý điền App Content & Data Safety:**
  - **Data collected / shared:** **No** (ứng dụng không có quyền `INTERNET`, không thu thập và không chia sẻ bất kỳ dữ liệu người dùng nào).
  - **App access:** Chọn *"All functionality is available without special access"* (không có đăng nhập).
  - **Ads:** **No**.
  - **Closed testing (nếu tài khoản cá nhân tạo sau 13/11/2023):** Cần tối thiểu **12 tester** bấm opt-in và cài đặt liên tục trong **14 ngày** trước khi xin mở Production.
  - **Lưu ý khi reviewer hỏi về `WRITE_SECURE_SETTINGS`:** Chỉ rõ tính năng mặc định (Mode A) hoạt động đầy đủ không cần quyền đặc biệt (đọc trạng thái trực tiếp, hướng dẫn theo hãng máy, nhảy thẳng tới công tắc trong Cài đặt, 4 Quick Settings tiles & 4 Widgets). `WRITE_SECURE_SETTINGS` chỉ là tuỳ chọn nâng cao do kỹ thuật viên/người dùng tự cấp qua ADB/Shizuku giống mô hình của *SystemUI Tuner*.
