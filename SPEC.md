# Specification: USB Debug Switch for Android

## 1. App Identity & Google Play Compliance

- **Package Name:** `io.github.l3n4k4.usbdebugswitch`
- **App Name:**
  - English (`res/values/strings.xml`): `USB Debug Switch`
  - Vietnamese (`res/values-vi/strings.xml`): `Bật Tắt Gỡ Lỗi USB`
  - Google Play listing title candidate: `USB Debug Switch for Android` (never start the title with `"Android ..."`)
- **Platform & Stack:**
  - Kotlin + Jetpack Compose (Material 3, with dynamic color on Android 12+ and a clean high-contrast fallback palette)
  - `minSdk = 26` (Android 8.0), `targetSdk = 35` (Android 15), `compileSdk = 35`
  - Bilingual resources: `res/values/strings.xml` (English default) and `res/values-vi/strings.xml` (natural, jargon-free Vietnamese)
- **Permissions (`AndroidManifest.xml`):**
  - ONLY declare:
    ```xml
    <uses-permission
        android:name="android.permission.WRITE_SECURE_SETTINGS"
        tools:ignore="ProtectedPermissions" />
    ```
  - **Strict prohibitions:** ZERO `INTERNET`, ZERO `ACCESS_NETWORK_STATE`, ZERO `FOREGROUND_SERVICE`, ZERO `WAKE_LOCK`, ZERO `RECEIVE_BOOT_COMPLETED`, ZERO ads, ZERO analytics/telemetry SDKs.
- **Launcher Icon:**
  - Custom vector adaptive icon (`ic_launcher_foreground`, `ic_launcher_background`, and Android 13+ `monochrome` layer) depicting a stylized USB plug + toggle switch.
  - Do **not** reuse or imitate the Android green robot or stock Android Settings gear icon.
- **Play Store Quality Safeguards:**
  - Must never appear as an empty "thin wrapper" or dead error screen when opened by a reviewer without ADB permission: the default unprivileged mode delivers full live status monitoring, OEM-aware onboarding, deep-link highlighting, Quick Settings Tile, and Home Screen Widget out of the box.
  - Auto-jump to Settings on app launch must remain **OFF by default** so first-time users and Play Store reviewers always see the main UI.

---

## 2. Settings Observation & Privileged Write Model

Read the three system flags from `Settings.Global` (reading requires zero permissions):

| Setting | `Settings.Global` Key | Min SDK |
|---|---|---|
| Developer Options | `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` (`"development_settings_enabled"`) | API 26+ |
| USB Debugging | `Settings.Global.ADB_ENABLED` (`"adb_enabled"`) | API 26+ |
| Wireless Debugging | `"adb_wifi_enabled"` | API 30 (Android 11)+ |

- Observe changes continuously via `ContentObserver` on all supported URIs, and refresh state + `WRITE_SECURE_SETTINGS` permission check (`checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED`) on `Lifecycle.Event.ON_RESUME`.

---

## 3. Single-Screen Hybrid UX ("Zero-Setup + Auto-Upgrade")

### Mode A: Unprivileged Mode (`WRITE_SECURE_SETTINGS` NOT granted — default out of the box)

#### State A1: Developer Mode is OFF (`development_settings_enabled == 0`)
- Detect device OEM via `Build.MANUFACTURER` / `Build.BRAND` into categories:
  - **Samsung** (`samsung`): *Thông tin phần mềm → chạm 7 lần vào **Số hiệu bản tạo*** (*Software information → tap **Build number** 7 times*)
  - **OPPO / Realme / OnePlus** (`oppo`, `realme`, `oneplus`): *Phiên bản → chạm 7 lần vào **Số phiên bản*** (*Version → tap **Version number** 7 times*)
  - **Xiaomi / Redmi / POCO** (`xiaomi`, `redmi`, `poco`): *Chạm 7 lần vào **Phiên bản hệ điều hành (HyperOS / MIUI)*** (*Tap **OS / MIUI version** 7 times*)
  - **Vivo / iQOO** (`vivo`, `iqoo`): *Thông tin phần mềm → chạm 7 lần vào **Số bản dựng*** (*Software information → tap **Build number** 7 times*)
  - **Pixel / Stock / Other**: *Kéo xuống cuối → chạm 7 lần vào **Số bản dựng*** (*Scroll to bottom → tap **Build number** 7 times*)
- Display **one concise step-by-step card** matching the detected brand, plus a compact *"Xem hãng khác"* / *"Other brands"* selector in case of a custom ROM or rebranded device.
- **One large primary button:** *"Mở tới chỗ bấm 7 lần"* / *"Open Build Number Screen"*
  - Fires `Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)` with:
    - `putExtra(":settings:fragment_args_key", "build_number")`
    - `putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", "build_number") })`
  - Wrapped in `try/catch (ActivityNotFoundException | SecurityException)` falling back to `Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)` without extras, and finally `Intent(Settings.ACTION_SETTINGS)`.
- Clear reminder note below the button: *"Nhập mật khẩu màn hình khoá nếu máy hỏi, rồi bấm Quay lại đây."* (*"Enter your screen lock PIN/password if prompted, then press Back to return here."*)
- Because `ContentObserver` + `ON_RESUME` watches `development_settings_enabled`, as soon as the user unlocks Developer Mode and returns, the app transitions directly into **State A2**.

#### State A2: Developer Mode is ON (`development_settings_enabled == 1`, `adb_enabled == 0` or `1`)
- **Status Hero Card:** Prominently displays live state badges:
  - **Gỡ lỗi USB (USB Debugging):** `Đang BẬT` / `Đang TẮT` (`ON` / `OFF`)
  - **Gỡ lỗi không dây (Wireless Debugging):** shown on Android 11+ (`Đang BẬT` / `Đang TẮT`)
  - **Chế độ nhà phát triển (Developer Mode):** `Đang BẬT`
- **One large primary button:**
  - When `adb_enabled == 0`: *"Đi tới BẬT Gỡ lỗi USB"* / *"Go to Turn ON USB Debugging"*
  - When `adb_enabled == 1`: *"Đi tới TẮT Gỡ lỗi USB"* / *"Go to Turn OFF USB Debugging"*
  - Fires `Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)` with `:settings:fragment_args_key = "enable_adb"` and `:settings:show_fragment_args = bundleOf(":settings:fragment_args_key" to "enable_adb")` (wrapped in `try/catch` falling back to plain `ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, then `ACTION_SETTINGS`) so Android Settings auto-scrolls to and highlights the **USB debugging** toggle.
- **Banking / VNeID Helper Card:**
  - Title: *"VNeID hoặc app ngân hàng báo lỗi?"* / *"Blocked by banking or ID apps?"*
  - 1-sentence explanation + secondary button: *"Mở đầu trang để tắt hẳn Chế độ nhà phát triển"* / *"Open top of Developer Options to turn off"* (launches `Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)` **without** `:settings:fragment_args_key`, and notes that turning off the master switch requires tapping Build Number 7 times next time unless 1-tap mode is enabled).
- **Optional Quick-Jump Preference (OFF by default):**
  - Stored in `SharedPreferences`: `"Tự nhảy thẳng tới công tắc Gỡ lỗi USB khi mở app"` / `"Auto-jump to USB Debugging toggle on launch"`. Only triggers when `development_settings_enabled == 1` and user has explicitly turned it on (still provide a brief delay or Back-return notice so users can disable the option if needed).

---

### Mode B: Privileged 1-Tap Mode (`WRITE_SECURE_SETTINGS` IS granted)

Automatically activates the moment `WRITE_SECURE_SETTINGS` permission is detected!

- Replaces the Settings redirect buttons with **direct 1-tap switches** right inside the app:
  1. **USB Debugging (`adb_enabled`):**
     - Switching **ON** when `development_settings_enabled == 0`: automatically writes `development_settings_enabled = 1` first, then `adb_enabled = 1` — the user never has to tap 7 times again.
     - Switching **OFF**: shows a quick confirmation dialog (*"Tắt Gỡ lỗi USB có thể ngắt kết nối cáp máy tính đang dùng. Tiếp tục?"*) before writing `adb_enabled = 0`.
  2. **Wireless Debugging (`adb_wifi_enabled`, Android 11+):**
     - Switching **ON** when `development_settings_enabled == 0`: automatically writes `development_settings_enabled = 1` first, then `adb_wifi_enabled = 1`.
     - Switching **OFF**: asks confirmation before writing `adb_wifi_enabled = 0`.
  3. **Developer Options (`development_settings_enabled`):**
     - Switching **OFF**: asks confirmation first, then cascades `adb_enabled = 0`, `adb_wifi_enabled = 0` (on Android 11+), and `development_settings_enabled = 0` so banking/VNeID apps pass cleanly without leaving hidden debug daemons active.
- Keep a small secondary link to open Android Settings directly if the user wants to inspect RSA authorizations or Wireless pairing codes.

---

### Collapsible Technician Footer (Both Modes)

- Collapsed by default so non-technical users see zero intimidating commands.
- Header: *"Dành cho kỹ thuật viên (Bật chế độ 1 chạm)"* / *"For technicians (Enable 1-tap mode)"*
- When expanded:
  - Shows current permission status (`Đã cấp` / `Chưa cấp`).
  - Displays copyable command (with Copy button using `ClipboardManager`):
    ```sh
    adb shell pm grant io.github.l3n4k4.usbdebugswitch android.permission.WRITE_SECURE_SETTINGS
    ```
  - Brief OEM notes:
    - OnePlus / OPPO (OxygenOS / ColorOS): enable **Disable permission monitoring** in Developer Options before running `pm grant`, then turn it back off.
    - Xiaomi (HyperOS / MIUI): enable **USB debugging (Security settings)** before running `pm grant`.

---

## 4. Quick Settings Tile & Home Screen Widget

### Quick Settings Tile (`UsbDebugTileService`)
- `TileService` showing `USB Debug` / `Gỡ lỗi USB` icon and active/inactive tile state synced from `Settings.Global.ADB_ENABLED`.
- On tile click:
  - Uses `unlockAndRun { ... }` so debugging cannot be toggled from a locked screen.
  - If `WRITE_SECURE_SETTINGS` is granted: toggles `adb_enabled` directly (if turning ON while `development_settings_enabled == 0`, sets `development_settings_enabled = 1` first), then refreshes tile state and Home Widget.
  - If `WRITE_SECURE_SETTINGS` is NOT granted:
    - Opens Developer Options highlighted on `enable_adb` if `development_settings_enabled == 1`, or opens `MainActivity` if Developer Mode is OFF.
    - Uses `PendingIntent` with `startActivityAndCollapse(PendingIntent)` on Android 14+ (API 34+) and `startActivityAndCollapse(Intent)` on API 26–33.

### Home Screen Widget (`UsbDebugWidgetProvider`)
- Implemented with pure `AppWidgetProvider` + `RemoteViews` (NO Jetpack Glance / `WorkManager` dependencies so no extra permissions or background scheduling).
- Clean 2×1 rounded pill widget displaying live USB Debugging state (`ON` / `OFF`) and a tap action:
  - When `WRITE_SECURE_SETTINGS` is granted: tapping either toggles or opens a lightweight transparent/dialog confirmation or direct toggle receiver; when unprivileged: tapping opens `MainActivity` or deep-links cleanly.
  - Refreshes automatically when the user toggles from inside the app or tile (`AppWidgetManager.ACTION_APPWIDGET_UPDATE`).

---

## 5. Debug Preview Hook (`BuildConfig.DEBUG` Only)

To allow automated UI inspection across every state and brand guide on a live test phone **without** mutating real `Settings.Global` flags (which could sever a remote USB ADB connection):

- Only active when `BuildConfig.DEBUG` is `true` (stripped or disabled in release builds).
- Supported Intent string extras on `.MainActivity`:
  - `debug_preview_state`: `"dev_off"`, `"adb_off"`, `"adb_on"`, `"privileged"`
  - `debug_brand`: `"samsung"`, `"xiaomi"`, `"oneplus"`, `"vivo"`, `"google"`
- When `debug_preview_state` is supplied in a debug build, render the corresponding simulated `DevState` + `hasWritePermission` and display a subtle `"DEBUG PREVIEW"` banner so tests can verify layout, Vietnamese/English text wrapping, button clicks, and dark/light themes cleanly.

---

## 6. Verification Requirements

1. **Unit Tests (`./gradlew testDebugUnitTest`):**
   - Brand detection mapping (`MANUFACTURER`/`BRAND` → OEM guide variant).
   - `DevSettings` state model, privilege auto-enable of `development_settings_enabled` before turning on `adb_enabled`/`adb_wifi_enabled`, and cascade off of `adb_enabled` + `adb_wifi_enabled` when turning off `development_settings_enabled`.
   - Intent builder validation (`enable_adb`, `build_number`, top-of-page Developer Options without fragment args).
2. **Build Verification:**
   - `./gradlew assembleDebug` succeeds cleanly.
   - `./gradlew assembleRelease` succeeds cleanly (minified/shrunk with R8; unsigned unless signing config provided) and merged manifest check confirms **no** `INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, or `RECEIVE_BOOT_COMPLETED` permissions leaked in.
3. **Live Hardware Verification:**
   - Install debug APK on test Android device, exercise all `debug_preview_state` + `debug_brand` combinations, inspect with `uiautomator dump` and `screencap`, and test the real Settings deep-link intents (`ACTION_DEVICE_INFO_SETTINGS` and `ACTION_APPLICATION_DEVELOPMENT_SETTINGS` with `:settings:fragment_args_key`).
