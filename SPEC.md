# Specification: Vibe Debug (USB Debug Switch for Android)

## 1. App Identity & Google Play Compliance

- **Package Name:** `io.github.xom11.vibedebug`
- **Version:** `2.0.0` (`versionCode = 3`)
- **App Name:**
  - English (`res/values/strings.xml`): `Vibe Debug`
  - Vietnamese (`res/values-vi/strings.xml`): `Vibe Debug`
  - Google Play listing title candidate: `Vibe Debug: USB & ADB Switch` (never start the title with `"Android ..."`)
- **Platform & Stack:**
  - Kotlin + Jetpack Compose (Material 3 with custom **Obsidian & Mint** dark/light `AppPalette` and System/Dark/Light selector)
  - `minSdk = 26` (Android 8.0), `targetSdk = 36` (Android 16), `compileSdk = 36`
  - Reproducible FLOSS build configuration: `dependenciesInfo { includeInApk = false; includeInBundle = false }`, `vcsInfo.include = false`, `LICENSE` (`GPL-3.0-or-later`), and `PRIVACY.md`.
  - Bilingual resources: `res/values/strings.xml` (English default) and `res/values-vi/strings.xml` (natural, jargon-free Vietnamese), plus `locales_config.xml` and Android 13+ `LocaleManager` in-app picker.
- **Permissions & Manifest (`AndroidManifest.xml`):**
  - `android:allowBackup="false"`
  - Standard `<queries>` for `ACTION_MAIN` + `CATEGORY_LAUNCHER` (allows Safe Launch app picker without `QUERY_ALL_PACKAGES`).
  - ONLY declare:
    ```xml
    <uses-permission
        android:name="android.permission.WRITE_SECURE_SETTINGS"
        tools:ignore="ProtectedPermissions" />
    ```
  - **Strict prohibitions:** ZERO `INTERNET`, ZERO `ACCESS_NETWORK_STATE`, ZERO `FOREGROUND_SERVICE`, ZERO `SYSTEM_ALERT_WINDOW`, ZERO `PACKAGE_USAGE_STATS`, ZERO `SCHEDULE_EXACT_ALARM`, ZERO `POST_NOTIFICATIONS`, ZERO `WAKE_LOCK`, ZERO `RECEIVE_BOOT_COMPLETED`, ZERO `QUERY_ALL_PACKAGES`, ZERO ads, ZERO analytics/telemetry SDKs.
- **Launcher Icon:**
  - Custom vector adaptive icon (`ic_launcher_foreground`, `ic_launcher_background`, and Android 13+ `monochrome` layer) depicting a stylized USB plug + toggle switch.
  - Do **not** reuse or imitate the Android green robot or stock Android Settings gear icon.
- **Play Store Quality Safeguards:**
  - Must never lock controls or appear as a thin wrapper when opened without ADB permission: default **Mode A** delivers full live monitoring, OEM-aware onboarding, deep-link highlighting, 4 Quick Settings tiles, and 4 1×1 icon-style Home Screen widgets out of the box.
  - Auto-jump to Settings on app launch remains **OFF by default** and is only displayed/active in Mode A.

---

## 2. Settings Observation, Enterprise Policy Guard & Privileged Write Model

Read the four system flags from `Settings.Global` (reading requires zero permissions):

| Setting | `Settings.Global` Key | Min SDK |
|---|---|---|
| Developer Options | `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` (`"development_settings_enabled"`) | API 26+ |
| USB Debugging | `Settings.Global.ADB_ENABLED` (`"adb_enabled"`) | API 26+ |
| Wireless Debugging | `"adb_wifi_enabled"` | API 30 (Android 11)+ |
| Keep Awake | `Settings.Global.STAY_ON_WHILE_PLUGGED_IN` (`"stay_on_while_plugged_in"`, mask `7` / `0`) | API 26+ |

- Check enterprise restriction `UserManager.DISALLOW_DEBUGGING_FEATURES` (`blockedByPolicy`). When blocked, surface a clear policy banner, disable debug toggles in UI/tiles/widget/Safe Launch, and never attempt to override device policies.
- Observe changes continuously via `ContentObserver` on all supported URIs, and refresh state + `WRITE_SECURE_SETTINGS` check (`checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED`) + `AutoOffManager.resync(...)` + `QuickSurfaceHelper.syncAllSurfaces(...)` on `Lifecycle.Event.ON_RESUME`.

---

## 3. Three-Tab Hybrid UX ("Zero-Setup Mode A + 1-Tap Mode B")

The app uses a responsive 3-tab layout (`HomeTab`, `ShortcutsTab`, `SetupTab`) styled with high-contrast Obsidian (`#0E1113` / `#7EE2A8`) and Crisp Daylight (`#F5F7FA` / `#059669`) palettes:
- **Compact portrait phones (`< 600dp`):** Bottom navigation bar (`BottomNavBar`) with single-column layout.
- **Tablets, foldables & wide/landscape windows (`>= 600dp` or compact-height landscape `>= 480dp`):** Left navigation rail (`SideNavRail`), display-cutout safe insets, centered `maxWidth` containers, and a 2-column dashboard layout from `>= 680dp` (`AdaptiveLayoutSpec`).

### Tab 1: Home (`HomeTab`)
- **Header & Status Strip:** App title, `Android X · API Y · Brand` mono subtitle, manual refresh icon, and top status strip (MDM policy banner if blocked, green Mint pill in Mode B, or Amber info strip with `"Enable 1-Tap"` button to SetupTab in Mode A).
- **Signature USB Hero Card:**
  - Prominent `52.sp` `ON` / `OFF` (`BẬT` / `TẮT`) headline, dynamic status/auto-off subtitle, and interactive **88dp circular power/action button**:
    - **Mode A (Unprivileged):** Opens Build Number screen when Developer Mode is OFF, or Developer Options highlighted on `enable_adb` when Developer Mode is ON.
    - **Mode B (Privileged):** Instant 1-tap toggle without confirmation popups (auto-enables Developer Mode first if currently OFF when turning ON).
  - Bottom `FlowRow` chips: `adb_enabled = 0/1`, clickable Mode B Auto-Off timer chip, or clickable Mode A Auto-Jump chip.
- **OEM Unlock Guide Card (Mode A when `development_settings_enabled == 0` and not blocked):**
  - Auto-detects manufacturer/brand (`Samsung`, `OPPO / Realme / OnePlus`, `Xiaomi / Redmi / POCO`, `Vivo / iQOO`, `Pixel / Stock / Other`), renders 2 brand-accurate steps with an *"Other brands"* chip selector, a primary button launching `ACTION_DEVICE_INFO_SETTINGS` with `:settings:fragment_args_key = "build_number"` (with safe fallbacks), and PIN reminder note.
- **Secondary Controls:**
  - **Wireless Debugging wide card** (Android 11+): instant 1-tap switch in Mode B; jumps to `toggle_adb_wireless` (or Build Number if Dev OFF) in Mode A.
  - **Equal-height 2-column cards (`IntrinsicSize.Max`):**
    - **Developer Options:** instant 1-tap cascade off (`adb_enabled=0`, `adb_wifi_enabled=0`, `development_settings_enabled=0`) in Mode B; jumps to top of Developer Options in Mode A (with a concise subtitle hint when ON in Mode A: *"On · Turn off if an app blocks debug"*).
    - **Keep Awake (`stay_on_while_plugged_in`):** 1-tap toggle in Mode B; opens Developer Options in Mode A.

### Tab 2: Shortcuts (`ShortcutsTab`)
- **Quick Controls (Quick Settings Tiles + 1×1 Icon Home Widgets):** Unified card where every control (**USB Debug**, **Wireless Debug** on Android 11+, **Developer Options**, **Keep Awake**) pairs its title/description with two aligned 50%/50% labeled pill buttons (`Quick Settings` | `Widget 1×1`). Quick Settings buttons use live added-state tracking (`QuickSurfaceHelper.readSurfaceState`, `BaseDebugTileService`, `AppPreferences`) and toggle via `StatusBarManager.requestAddTileService` (or manual instructions dialog pre-API 33) and component disable + `sysui_qs_tiles` cleanup. Widget buttons track active `AppWidgetManager` IDs per `QuickTileType` and pin via `AppWidgetManager.requestPinAppWidget` or guide users to long-press their Home screen to remove.
- **Turn Off Debug & Launch (`SafeLaunchHelper` + `SafeLaunchActivity`):**
  - **Empty state:** Compact `SurfaceCard` explaining strict-app Clean Launch with a `"Choose apps"` button.
  - **Populated state:** Concise 1-line hint above saved app cards (**Open now** and **Pin / Remove shortcut** with 192×192 badged app icon with `#0E1113`/`#7EE2A8` ring), followed by `"Add / edit apps"`.
  - Searchable launcher app picker loaded off the main thread (`Dispatchers.IO`), prioritizing common strict Vietnamese banking / digital ID apps (`VNeID`, `Vietcombank`, `VPBank`, `VietinBank`, `BIDV`, `Techcombank`, `MB`, `TPBank`, `ACB`, `MoMo`, `ZaloPay`, `Cake`, `Viettel Money`) with a generic `"Suggested"` badge while supporting any installed app (useful for banking, digital ID, e-wallet, and security-sensitive apps).
  - `SafeLaunchActivity` validates the requested package against the user's saved `safeLaunchPackages` allowlist before calling `controller.setDeveloperOptions(false)`, cancelling Auto-Off, syncing surfaces, and starting the app (Mode B), or directing to Developer Options top (Mode A).

### Tab 3: Setup (`SetupTab`)
- **1-Tap Mode Setup Checklist & OEM ADB Guide:**
  - 2-step checklist (Developer Mode + `WRITE_SECURE_SETTINGS` permission status) and `"Re-check permission"` button.
  - OEM-tailored ADB instructions with full-width `CommandCopyBox` (monospace command + bottom-right Copy button):
    - **OPPO / OnePlus / Realme:** Highlights `adb shell pm path io.github.xom11.vibedebug | tr -d '\r' | sed 's/package://' | xargs -n1 adb pull && adb install-multiple -r -g *.apk` first for ColorOS/OxygenOS 15–16+ (where `pm grant` throws `SecurityException`), with collapsible legacy *Disable permission monitoring* + `pm grant` note.
    - **Xiaomi / Redmi / POCO:** Shows `pm grant`, HyperOS/MIUI *"USB debugging (Security settings)"* note, and reinstall fallback.
    - **Other brands:** Shows `pm grant` plus reinstall fallback.
- **Safety Auto-Off Timer (Mode B only):**
  - Toggle + `15 / 30 / 60 min` segmented control (disabled with clear *"Requires 1-Tap Mode"* helper text in Mode A). Uses standard inexact `AlarmManager.setWindow` via `AutoOffManager` & `AutoOffReceiver`.
- **Appearance & Behavior:**
  - Theme selector (`System` | `Dark` | `Light`).
  - In-app Language selector (`System` | `Tiếng Việt` | `English`) on Android 13+.
  - Auto-jump to USB Debugging on launch toggle (shown only in Mode A; OFF by default).
- **Privacy & Version Card:** Zero network, zero background services guarantee + version readout.

---

## 4. Quick Settings Tiles & Home Screen Widget

- **`BaseDebugTileService`:** Shared `TileService` base registering a `ContentObserver` on all 4 global keys during `onStartListening` and using `unlockAndRun` + API 34 `PendingIntent` / pre-34 `Intent` `startActivityAndCollapse`.
  - `UsbDebugTileService` (Mode B toggles + triggers `AutoOffManager.scheduleIfEnabled`, displays remaining minutes in subtitle; Mode A highlights `enable_adb`)
  - `WirelessDebugTileService` (Android 11+, handles pre-Android 11 gracefully)
  - `DevOptionsTileService` (Mode B cascades off debugging + cancels Auto-Off; Mode A opens top of Developer Options)
  - `KeepAwakeTileService` (Mode B toggles `stay_on_while_plugged_in`; Mode A opens Developer Options)
- **`BaseDebugWidgetProvider` (`UsbDebugWidgetProvider`, `WirelessDebugWidgetProvider`, `DevOptionsWidgetProvider`, `KeepAwakeWidgetProvider`) + `WidgetToggleReceiver` (`android:exported="false"`):** Pure `AppWidgetProvider` + `RemoteViews` 1×1 icon-style Obsidian & Mint squircle widgets for all 4 controls, synced across app, tiles, and `AutoOffReceiver`. Privileged toggle actions are dispatched exclusively to the non-exported `WidgetToggleReceiver` so third-party apps cannot trigger privileged toggles via exported widget broadcasts.

---

## 5. Debug Preview Hook (`BuildConfig.DEBUG` Only)

Only active when `BuildConfig.DEBUG` is `true`:
- `.MainActivity` extras:
  - `debug_preview_state`: `"dev_off"`, `"adb_off"`, `"adb_on"`, `"privileged"`, `"privileged_off"`, `"blocked"`
  - `debug_brand`: `"samsung"`, `"xiaomi"`, `"oneplus"`, `"vivo"`, `"google"`
  - `debug_tab`: `"home"`, `"shortcuts"`, `"setup"`

---

## 6. Verification Requirements

1. **Unit Tests (`./gradlew testDebugUnitTest`):**
   - `DevSettingsControllerTest`, `OemBrandDetectorTest`, `SettingsIntentsTest`, `QuickSurfaceHelperTest`, `AutoOffAndPreferencesTest`.
2. **Build & Manifest Verification:**
   - `./gradlew assembleDebug` and `./gradlew assembleRelease` succeed cleanly (R8 minification + resource shrinking + `lintVitalRelease`).
   - Merged release manifest contains ONLY `WRITE_SECURE_SETTINGS` (+ standard AndroidX `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`), `allowBackup="false"`, and launcher `<queries>`.
3. **Live Hardware Verification:**
   - Install debug APK on test device, exercise `debug_preview_state` + `debug_brand` + `debug_tab` combinations, inspect screenshots / `uiautomator dump`, and test real Settings deep-link intents without turning off real `development_settings_enabled` or `adb_enabled`.
