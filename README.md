# Vibe Debug — USB Debug Switch for Android

A clean, zero-ad, zero-internet-permission Android utility (**Vibe Debug**, `io.github.l3n4k4.usbdebugswitch`) built for everyday users and developers to inspect and switch **USB Debugging** without digging through nested Settings menus.

## Key Capabilities

- **Zero-setup guided flow out of the box (Mode A):**
  - Detects whether Developer Mode is unlocked. If not, detects the device manufacturer (Samsung, OPPO/Realme/OnePlus, Xiaomi/Redmi/POCO, Vivo/iQOO, Google/Stock Android) and displays a focused 2-step guide matching that brand's exact menu labels, plus a button that jumps directly to **About Phone / Build Number**.
  - Once Developer Mode is unlocked, displays live status for **USB Debugging**, **Wireless Debugging** (Android 11+), **Developer Mode**, and **Keep Awake**, with an interactive Hero button that opens **Developer Options** and highlights **USB debugging** (`:settings:fragment_args_key = "enable_adb"`).
  - Surfaces a concise hint right on the **Developer Options** card to turn it off when strict apps block debugging, plus an optional Mode A quick-jump preference (OFF by default).
- **Automatic 1-tap upgrade (`WRITE_SECURE_SETTINGS`, Mode B):**
  - Once `android.permission.WRITE_SECURE_SETTINGS` is granted once via ADB or Shizuku (including the `adb install-multiple -r -g` flow for ColorOS / OxygenOS 15–16+), the app automatically upgrades into **1-Tap Mode** across the app, 4 Quick Settings tiles, and 4 1×1 icon-style Home Screen widgets (**USB Debug**, **Wireless Debug**, **Developer Options**, **Keep Awake**).
  - **Turn Off Debug & Launch (Clean Launch):** In **Shortcuts**, select any strict app (banking, digital ID, e-wallet, security-sensitive or enterprise apps) to turn off Developer Options & debugging cleanly in 1 tap before opening — or pin replacement 1×1 home shortcuts with zero foreground services or `PACKAGE_USAGE_STATS`.
  - **Safety Auto-Off Timer (15 / 30 / 60 min):** Uses Android's battery-friendly inexact `AlarmManager.setWindow` (no `SCHEDULE_EXACT_ALARM`, no background service) to turn USB Debugging back off automatically.
- **Crafted adaptive 3-tab Obsidian & Mint UI:**
  - Home, Shortcuts, and Setup tabs adapting automatically between phone portrait (`BottomNavBar` single-column) and foldables / tablets / landscape (`SideNavRail` + 2-column dashboard), with System / Dark / Light theme switching, Android 13+ in-app English/Vietnamese language switching, and MDM enterprise policy awareness (`UserManager.DISALLOW_DEBUGGING_FEATURES`).
- **Google Play & F-Droid privacy by design:** Only declares `WRITE_SECURE_SETTINGS` (plus launcher `<queries>` for Safe Launch app selection). Zero `INTERNET`, zero `FOREGROUND_SERVICE`, zero `SYSTEM_ALERT_WINDOW`, zero `PACKAGE_USAGE_STATS`, zero `QUERY_ALL_PACKAGES`, zero ads, zero analytics, and disabled AGP `dependenciesInfo` / `vcsInfo` for clean F-Droid reproducible builds.

## Privacy

See [PRIVACY.md](PRIVACY.md) for the full bilingual privacy policy (English & Tiếng Việt).

## License

Licensed under the [GNU General Public License v3.0 or later (GPL-3.0-or-later)](LICENSE).

## Specification

See [SPEC.md](SPEC.md) for the complete UX, architecture, and compliance specification.
