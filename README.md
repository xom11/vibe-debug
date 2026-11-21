# USB Debug Switch for Android

A minimal, zero-ad, zero-internet-permission Android utility (`io.github.l3n4k4.usbdebugswitch`) built for everyday users to check and switch **USB Debugging** without hunting through nested settings menus.

## Key Capabilities

- **Zero-setup guided flow (out of the box):**
  - Detects whether Developer Mode is unlocked. If not, detects the device manufacturer (Samsung, Xiaomi/Redmi/POCO, OPPO/Realme/OnePlus, Vivo/iQOO, Google/Stock Android) and shows a single, clear instruction card matching that brand's exact menu labels, plus a button that jumps straight to **About Phone**.
  - Once Developer Mode is unlocked, displays live status for **USB Debugging**, **Wireless Debugging** (Android 11+), and **Developer Mode**, and provides a single button that opens **Developer Options** and auto-scrolls/highlights **USB debugging** (`:settings:fragment_args_key = "enable_adb"`).
  - Includes a **Banking / VNeID helper** card to jump to the top of Developer Options when users need to turn Developer Mode completely off.
- **Automatic 1-tap upgrade (`WRITE_SECURE_SETTINGS`):**
  - If a technician or user grants `android.permission.WRITE_SECURE_SETTINGS` once via ADB or Shizuku, the app automatically unlocks direct 1-tap switches in the app, Quick Settings tile, and Home Screen widget — including automatically re-enabling Developer Mode whenever USB or Wireless Debugging is switched back on.
- **Privacy by design:** No ads, no analytics, no `INTERNET` permission, no background services (`WorkManager`/foreground service).

## Specification

See [SPEC.md](SPEC.md) for the complete UX, architecture, and Google Play compliance specification.
