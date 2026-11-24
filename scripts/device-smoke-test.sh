#!/usr/bin/env bash
set -euo pipefail

PKG="io.github.xom11.vibedebug"
ADB_PREFIX="${ADB_PREFIX:-adb}"
INSTALL_APK=""
OUT_DIR="./build/device-smoke"
SERIALS=()

usage() {
    cat <<'EOF'
Usage: device-smoke-test.sh [--install <apk>] [--out-dir <dir>] [--adb-prefix "<cmd>"] [serial ...]

Runs a non-destructive smoke test across one or more connected Android devices:
- Records baseline development_settings_enabled & adb_enabled (never turns them off)
- Optionally installs APK in-place (-r, never uninstalls)
- Prints OEM/launcher/pin-capability matrix
- Verifies Settings deep-link intent resolution & Quick Settings/Widget registrations
- Launches Home, Shortcuts, and Setup tabs, captures screenshots, and checks logcat for crashes
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --install)
            INSTALL_APK="${2:-}"
            shift 2
            ;;
        --out-dir)
            OUT_DIR="${2:-}"
            shift 2
            ;;
        --adb-prefix)
            ADB_PREFIX="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            SERIALS+=("$1")
            shift
            ;;
    esac
done

read -r -a ADB_CMD <<< "$ADB_PREFIX"

run_adb() {
    if [[ "${ADB_CMD[0]}" == "ssh" ]]; then
        local remote_host="${ADB_CMD[1]}"
        local quoted_args=()
        local arg
        for arg in "${ADB_CMD[@]:2}" "$@"; do
            quoted_args+=("$(printf '%q' "$arg")")
        done
        ssh "$remote_host" "${quoted_args[*]} </dev/null"
    else
        "${ADB_CMD[@]}" "$@"
    fi
}

run_shell() {
    local serial="$1"
    local script="$2"
    run_adb -s "$serial" shell -n "$script"
}

if [[ ${#SERIALS[@]} -eq 0 ]]; then
    while IFS= read -r line; do
        [[ -n "$line" ]] && SERIALS+=("$line")
    done < <(run_adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi

if [[ ${#SERIALS[@]} -eq 0 ]]; then
    echo "No connected ADB devices found (ADB_PREFIX='$ADB_PREFIX')." >&2
    exit 1
fi

mkdir -p "$OUT_DIR"

install_apk_on_device() {
    local serial="$1"
    local apk="$2"
    if [[ ! -f "$apk" ]]; then
        echo "APK not found: $apk" >&2
        exit 1
    fi
    if [[ "${ADB_CMD[0]}" == "ssh" ]]; then
        local remote_host="${ADB_CMD[1]}"
        local remote_tmp="/tmp/vd_smoke_${RANDOM}.apk"
        ssh "$remote_host" "cat > '$remote_tmp'" < "$apk"
        ssh "$remote_host" "adb -s $(printf '%q' "$serial") install -r '$remote_tmp'; rc=\$?; rm -f '$remote_tmp'; exit \$rc" </dev/null
    else
        run_adb -s "$serial" install -r "$apk"
    fi
}

failures=0

for serial in "${SERIALS[@]}"; do
    echo "============================================================"
    echo "DEVICE: $serial"
    echo "============================================================"

    base_dev=$(run_shell "$serial" "settings get global development_settings_enabled" | tr -d '\r')
    base_adb=$(run_shell "$serial" "settings get global adb_enabled" | tr -d '\r')

    if [[ -n "$INSTALL_APK" ]]; then
        echo "Installing $INSTALL_APK (in-place -r)..."
        install_apk_on_device "$serial" "$INSTALL_APK"
    fi

    run_shell "$serial" "
        echo \"OEM: \$(getprop ro.product.manufacturer) / \$(getprop ro.product.brand) / \$(getprop ro.product.model) | Android \$(getprop ro.build.version.release) (API \$(getprop ro.build.version.sdk))\"
        echo \"Launcher: \$(cmd role get-role-holders android.app.role.HOME)\"
        echo \"PinWidget: \$(cmd package query-activities --brief -a android.content.pm.action.CONFIRM_PIN_APPWIDGET | tail -n 1)\"
        echo \"PinShortcut: \$(cmd package query-activities --brief -a android.content.pm.action.CONFIRM_PIN_SHORTCUT | tail -n 1)\"
        echo \"Package: \$(dumpsys package $PKG | grep -E 'versionName|versionCode|WRITE_SECURE_SETTINGS: granted' | tr '\n' ' ')\"
        echo \"Intent DeviceInfo: \$(cmd package resolve-activity --brief -a android.settings.DEVICE_INFO_SETTINGS | tail -n 1)\"
        echo \"Intent DevSettings: \$(cmd package resolve-activity --brief -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS | tail -n 1)\"
        echo \"Intent UsageAccess: \$(cmd package resolve-activity --brief -a android.settings.USAGE_ACCESS_SETTINGS | tail -n 1)\"
        echo \"Intent AppDetails: \$(cmd package resolve-activity --brief -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$PKG | tail -n 1)\"
        echo \"Intent MiuiPermEditor: \$(cmd package resolve-activity --brief -a miui.intent.action.APP_PERM_EDITOR | tail -n 1)\"
        echo \"WidgetProvider: \$(dumpsys appwidget | grep -c $PKG || true)\"
        echo \"TileServices: \$(dumpsys package $PKG | grep -cE 'UsbDebugTileService|WirelessDebugTileService' || true)\"
    "

    safe_serial="${serial//[:\/]/_}"
    run_shell "$serial" "
        input keyevent KEYCODE_WAKEUP
        wm dismiss-keyguard
        logcat -c
        for tab in home shortcuts setup; do
            am start -n $PKG/.MainActivity --es open_tab \$tab >/dev/null
            sleep 1.2
            screencap -p /sdcard/vd_smoke_\${tab}.png
        done
    "

    for tab in home shortcuts setup; do
        run_adb -s "$serial" exec-out cat "/sdcard/vd_smoke_${tab}.png" > "${OUT_DIR}/${safe_serial}_${tab}.png"
    done

    crash_log=$(run_shell "$serial" "logcat -d -s AndroidRuntime:E | grep -E 'FATAL EXCEPTION|$PKG' || true")
    run_shell "$serial" "rm -f /sdcard/vd_smoke_*.png"

    end_dev=$(run_shell "$serial" "settings get global development_settings_enabled" | tr -d '\r')
    end_adb=$(run_shell "$serial" "settings get global adb_enabled" | tr -d '\r')

    if [[ "$base_dev" != "$end_dev" || "$base_adb" != "$end_adb" ]]; then
        echo "SAFETY FAILURE: debug flags mutated ($base_dev/$base_adb -> $end_dev/$end_adb)" >&2
        failures=$((failures + 1))
    fi

    if [[ -n "$crash_log" ]]; then
        echo "CRASH DETECTED on $serial:" >&2
        echo "$crash_log" >&2
        failures=$((failures + 1))
    else
        echo "Result: PASS (0 crashes, debug flags preserved: dev=$end_dev adb=$end_adb)"
    fi
done

exit "$failures"
