# Privacy Policy — Vibe Debug (USB Debug Switch)

**Effective date:** 2026-11-23  
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
4. **Installed App Visibility (`ACTION_MAIN` / `CATEGORY_LAUNCHER`):**
   - Used solely on-device inside the *Turn Off Debug & Launch* picker so you can choose which installed launcher apps to include in your personal allowlist.
   - This app list is never stored off-device or transmitted anywhere.
5. **Local Preferences (`android:allowBackup="false"`):**
   - Your selected theme, Safe Launch allowlist, per-app modes, auto-off timer duration, and quick-tile flags are stored locally in private `SharedPreferences` with Android cloud backup disabled. Uninstalling the app deletes all local preferences immediately.

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
4. **Danh sách ứng dụng đã cài (`ACTION_MAIN` / `CATEGORY_LAUNCHER`):**
   - Chỉ được dùng cục bộ trong hộp thoại chọn app của tính năng *Tắt gỡ lỗi & Mở app* để bạn tự chọn ứng dụng cần bảo vệ; tuyệt đối không ghi ra ngoài hay gửi đi đâu.
5. **Lưu trữ cục bộ (`android:allowBackup="false"`):**
   - Giao diện, danh sách app, chế độ từng app và cài đặt hẹn giờ chỉ lưu trong `SharedPreferences` riêng của ứng dụng và đã tắt sao lưu đám mây; xoá app là xoá sạch dữ liệu ngay lập tức.
