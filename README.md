# VibeADB — USB Debug Switch for Android

A clean, zero-ad, zero-internet-permission Android utility (**VibeADB**, `io.github.l3n4k4.usbdebugswitch`) built for everyday users and developers to inspect and switch **USB Debugging** without digging through nested Settings menus.

## Key Capabilities

- **Zero-setup guided flow out of the box (Mode A):**
  - Detects whether Developer Mode is unlocked. If not, detects the device manufacturer (Samsung, OPPO/Realme/OnePlus, Xiaomi/Redmi/POCO, Vivo/iQOO, Google/Stock Android) and displays a focused 2-step guide matching that brand's exact menu labels, plus a button that jumps directly to **About Phone / Build Number**.
  - Once Developer Mode is unlocked, displays live status for **USB Debugging**, **Wireless Debugging** (Android 11+), **Developer Mode**, and **Keep Awake**, with an interactive Hero button that opens **Developer Options** and highlights **USB debugging** (`:settings:fragment_args_key = "enable_adb"`).
  - Includes a **Banking / VNeID helper** card to jump to the top of Developer Options when banking or ID apps require turning Developer Mode completely off, plus an optional Mode A quick-jump preference (OFF by default).
- **Automatic 1-tap upgrade (`WRITE_SECURE_SETTINGS`, Mode B):**
  - Once `android.permission.WRITE_SECURE_SETTINGS` is granted once via ADB or Shizuku (including the `adb install-multiple -r -g` flow for ColorOS / OxygenOS 15–16+), the app automatically upgrades into **1-Tap Mode** across the app, 4 Quick Settings tiles (**USB Debug**, **Wireless Debug**, **Developer Options**, **Keep Awake**), and the 2×1 Home Screen widget.
  - **Banking / VNeID Safe Launch:** Pin badged 1×1 home shortcuts that disable Developer Options & debugging cleanly right before launching banking or digital ID apps — with zero foreground services or `PACKAGE_USAGE_STATS`.
  - **Safety Auto-Off Timer (15 / 30 / 60 min):** Uses Android's battery-friendly inexact `AlarmManager.setWindow` (no `SCHEDULE_EXACT_ALARM`, no background service) to turn USB Debugging back off automatically.
- **Crafted 3-tab Obsidian & Mint UI:**
  - Home, Shortcuts, and Setup tabs with System / Dark / Light theme switching, Android 13+ in-app English/Vietnamese language switching, and MDM enterprise policy awareness (`UserManager.DISALLOW_DEBUGGING_FEATURES`).
- **Google Play privacy by design:** Only declares `WRITE_SECURE_SETTINGS` (plus launcher `<queries>` for Safe Launch app selection). Zero `INTERNET`, zero `FOREGROUND_SERVICE`, zero `SYSTEM_ALERT_WINDOW`, zero `PACKAGE_USAGE_STATS`, zero `QUERY_ALL_PACKAGES`, zero ads or analytics.

## Specification

See [SPEC.md](SPEC.md) for the complete UX, architecture, and Google Play compliance specification.
