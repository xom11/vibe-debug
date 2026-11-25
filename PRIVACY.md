# Privacy Policy — Vibe Debug (USB Debug Switch)

**Effective date:** 2026-11-23  
**Last updated:** 2026-11-24  
**Developer:** xom11 (<https://github.com/xom11>)  
**Contact:** <https://github.com/xom11/vibe-debug/issues>  
**License:** GPL-3.0-or-later  

## English

**Vibe Debug** does **not** collect, store remotely, transmit, track, sell, or share any personal or device data.

1. **Zero Network Access:** The app does not declare `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`. It cannot make network requests and contains zero analytics, crash-reporting SDKs, or advertising libraries.
2. **Local-Only Settings Inspection & Control:**
   - Reading developer settings state (`development_settings_enabled`, `adb_enabled`, `adb_wifi_enabled`, `stay_on_while_plugged_in`) happens entirely on-device via Android `Settings.Global`.
   - `android.permission.WRITE_SECURE_SETTINGS` (optional, granted manually by the user via ADB or Shizuku) is used strictly to flip those local developer flags when you explicitly tap a toggle, Quick Settings tile, Home Screen widget, Clean Launch shortcut, when your configured local Auto-Off timer expires, or when your optional on-device per-app rules are active.
3. **Optional On-Device Per-App Auto Watcher (`PACKAGE_USAGE_STATS` & `FOREGROUND_SERVICE_SPECIAL_USE`):**
   - Disabled by default. Only when you explicitly enable *"Auto-turn off when opening apps"* and grant Usage Access does Vibe Debug check the current foreground package on-device (pausing automatically while the screen is off) so debugging turns off while your chosen strict apps are open and restores afterward.
   - Foreground package checks never use `AccessibilityService`, and no usage history is ever stored, logged, or sent off-device.
4. **Installed App Visibility & On-Device Anti-Debug Scanning (`ACTION_MAIN` / `CATEGORY_LAUNCHER`):**
   - Used solely on-device inside *Turn Off Debug & Launch* to let you choose installed launcher apps, match them against the built-in catalog, and inspect local APK package signatures/DEX anti-debug strings (when running auto-detection or Deep Scan) to suggest strict apps automatically.
   - Only boolean detection flags and SDK names keyed by `(packageName, lastUpdateTime)` are cached in local `SharedPreferences`; this list and scan data are never sent off-device.
5. **Local Preferences (`android:allowBackup="false"`):**
   - Your selected theme, Safe Launch allowlist, per-app modes, auto-off timer duration, and quick-tile flags are stored locally in private `SharedPreferences` with Android cloud backup disabled. Uninstalling the app deletes all local preferences immediately.
6. **Device Diagnostics (user-initiated only):**
   - The *Copy device diagnostics* button in Setup copies a plain-text report (app version, device manufacturer/model, Android version, locale, permission and debug-setting states, home launcher package, and which widget/tile surfaces are added) to your clipboard. It contains no account, contact, location, or usage-history data.
   - Vibe Debug never sends this report anywhere; it only leaves your device if you paste it somewhere yourself.
7. **Children:** Vibe Debug is a developer utility and is not directed at children.
8. **Changes & Contact:**
   - Any change to this policy is published in this file with a new *Last updated* date.
   - Questions or requests: open an issue at <https://github.com/xom11/vibe-debug/issues>.

---

## Tiếng Việt

**Vibe Debug** **không** thu thập, lưu trữ từ xa, truyền tải, theo dõi, mua bán hay chia sẻ bất kỳ dữ liệu cá nhân hoặc thông tin thiết bị nào.

1. **Không truy cập Internet:** Ứng dụng không khai báo quyền `INTERNET` hay `ACCESS_NETWORK_STATE`, không thể kết nối mạng và hoàn toàn không chứa SDK phân tích, theo dõi hay quảng cáo.
2. **Kiểm tra & điều khiển hoàn toàn cục bộ trên máy:**
   - Việc đọc trạng thái `development_settings_enabled`, `adb_enabled`, `adb_wifi_enabled`, `stay_on_while_plugged_in` chỉ diễn ra cục bộ qua `Settings.Global`.
   - Quyền `android.permission.WRITE_SECURE_SETTINGS` (tuỳ chọn, do chính người dùng chủ động cấp qua ADB hoặc Shizuku) chỉ dùng để bật/tắt các cờ gỡ lỗi này khi bạn trực tiếp bấm nút trong ứng dụng, ô Cài đặt nhanh, Widget, lối tắt *Tắt gỡ lỗi & Mở app*, khi bộ hẹn giờ tự tắt hết hạn, hoặc khi bạn bật chế độ tự tắt theo ứng dụng.
3. **Chế độ tự tắt gỡ lỗi theo ứng dụng (`PACKAGE_USAGE_STATS` & `FOREGROUND_SERVICE_SPECIAL_USE`):**
   - Tắt mặc định. Chỉ khi bạn chủ động bật *"Tự động tắt khi mở app"* và cấp quyền Truy cập dữ liệu sử dụng, Vibe Debug mới kiểm tra cục bộ ứng dụng đang mở trên màn hình (tự nghỉ hoàn toàn khi tắt màn hình) để tắt gỡ lỗi khi vào app đã chọn và tự bật lại khi thoát.
   - Không dùng `AccessibilityService`, không lưu lịch sử sử dụng và không truyền bất kỳ dữ liệu nào ra ngoài thiết bị.
4. **Danh sách ứng dụng & quét nhận diện cục bộ (`ACTION_MAIN` / `CATEGORY_LAUNCHER`):**
   - Chỉ được dùng cục bộ trong tính năng *Tắt gỡ lỗi & Mở app* để hiển thị các ứng dụng đã cài, đối chiếu với danh sách gợi ý và kiểm tra chữ ký RASP / chuỗi chống gỡ lỗi trong APK ngay trên máy (khi quét tự động hoặc Quét sâu).
   - Chỉ lưu cờ nhận diện dạng cục bộ theo `(tên gói, thời gian cập nhật)` trong `SharedPreferences`; dữ liệu tuyệt đối không bao giờ được gửi ra ngoài thiết bị.
5. **Lưu trữ cục bộ (`android:allowBackup="false"`):**
   - Giao diện, danh sách app, chế độ từng app và cài đặt hẹn giờ chỉ lưu trong `SharedPreferences` riêng của ứng dụng và đã tắt sao lưu đám mây; xoá app là xoá sạch dữ liệu ngay lập tức.
6. **Thông tin chẩn đoán (chỉ khi bạn bấm):**
   - Nút *Sao chép thông tin chẩn đoán* trong tab Thiết lập chép vào bộ nhớ tạm một báo cáo dạng văn bản (phiên bản app, hãng/dòng máy, phiên bản Android, ngôn ngữ, trạng thái quyền và cài đặt gỡ lỗi, gói launcher đang dùng, widget/ô Cài đặt nhanh đã thêm). Báo cáo không chứa tài khoản, danh bạ, vị trí hay lịch sử sử dụng.
   - Vibe Debug không gửi báo cáo này đi đâu; nó chỉ rời khỏi máy khi chính bạn dán vào nơi khác.
7. **Trẻ em:** Vibe Debug là công cụ cho nhà phát triển, không hướng tới trẻ em.
8. **Thay đổi & liên hệ:**
   - Mọi thay đổi của chính sách này được cập nhật trong file này kèm ngày *Last updated* mới.
   - Câu hỏi hoặc yêu cầu: mở issue tại <https://github.com/xom11/vibe-debug/issues>.
