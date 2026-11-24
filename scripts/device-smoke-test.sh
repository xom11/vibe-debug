#!/usr/bin/env bash
set -euo pipefail

PKG="io.github.xom11.vibedebug"
ADB_PREFIX="${ADB_PREFIX:-adb}"
INSTALL_APK=""
OUT_DIR="./build/device-smoke"
EXTENDED=0
SERIALS=()

usage() {
    cat <<'EOF'
Usage: device-smoke-test.sh [--install <apk>] [--out-dir <dir>] [--adb-prefix "<cmd>"] [--extended] [serial ...]

Runs a non-destructive smoke test across one or more connected Android devices:
- Records baseline development_settings_enabled & adb_enabled (never turns them off)
- Optionally installs APK in-place (-r, never uninstalls)
- Prints OEM/launcher/pin-capability matrix (+ MIUIOP 10017 state on Xiaomi)
- Verifies Settings deep-link intent resolution & Quick Settings/Widget registrations
- Launches Home, Shortcuts, and Setup tabs, captures screenshots, and checks logcat for crashes
- With --extended: tests live Settings launches, Light/Dark themes, Vietnamese + 1.3x font scale,
  and debug preview states (only when the installed build is DEBUGGABLE), restoring original UI settings.
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
        --extended)
            EXTENDED=1
            shift
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

restore_device_ui() {
    local serial="$1"
    local night="$2"
    local font="$3"
    local sdk="$4"
    local locales="$5"
    run_shell "$serial" "
        if [ -n '$night' ]; then
            cmd uimode night '$night' >/dev/null 2>&1 || true
        fi
        if [ -z '$font' ] || [ '$font' = 'null' ]; then
            settings delete system font_scale >/dev/null 2>&1 || true
        else
            settings put system font_scale '$font' >/dev/null 2>&1 || true
        fi
        if [ '${sdk:-0}' -ge 33 ] 2>/dev/null; then
            if [ -z '$locales' ]; then
                cmd locale set-app-locales $PKG >/dev/null 2>&1 || true
            else
                cmd locale set-app-locales $PKG --locales '$locales' >/dev/null 2>&1 || true
            fi
        fi
    " || true
}

capture_shot() {
    local serial="$1"
    local name="$2"
    run_shell "$serial" "screencap -p /sdcard/${name}.png"
    run_adb -s "$serial" exec-out cat "/sdcard/${name}.png" > "${OUT_DIR}/${name}.png"
    run_shell "$serial" "rm -f /sdcard/${name}.png"
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

    pkg_flags=$(run_shell "$serial" "dumpsys package $PKG | grep 'pkgFlags=' | head -n 1" | tr -d '\r')
    oem_brand=$(run_shell "$serial" "getprop ro.product.manufacturer; getprop ro.product.brand" | tr '\r\n' ' ')
    orig_sdk=$(run_shell "$serial" "getprop ro.build.version.sdk" | tr -d '\r')

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

    if echo "$oem_brand" | grep -qiE "xiaomi|redmi|poco"; then
        run_shell "$serial" "echo \"MIUIOP(10017): \$(cmd appops get $PKG 10017 2>&1 || true)\""
    fi

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
    run_shell "$serial" "rm -f /sdcard/vd_smoke_*.png"

    if [[ "$EXTENDED" -eq 1 ]]; then
        echo "--- Extended checks on $serial ---"
        orig_night=$(run_shell "$serial" "cmd uimode night 2>/dev/null | awk '{print \$NF}'" | tr -d '\r')
        orig_font_scale=$(run_shell "$serial" "settings get system font_scale 2>/dev/null" | tr -d '\r')
        orig_locales=""
        if [[ "${orig_sdk:-0}" -ge 33 ]]; then
            parsed_loc=$(run_shell "$serial" "cmd locale get-app-locales $PKG 2>/dev/null | sed -n 's/.*\\[\\(.*\\)\\].*/\\1/p'" | tr -d '\r ')
            if [[ -n "$parsed_loc" && "$parsed_loc" != "null" && "$parsed_loc" != "empty" ]]; then
                orig_locales="$parsed_loc"
            fi
        fi

        ext_ok=0
        {
            echo "Live Settings Intent launches:"
            run_shell "$serial" "
                for act in \
                    android.settings.DEVICE_INFO_SETTINGS \
                    android.settings.APPLICATION_DEVELOPMENT_SETTINGS \
                    android.settings.USAGE_ACCESS_SETTINGS \
                    android.settings.APPLICATION_DETAILS_SETTINGS; do
                    if [ \"\$act\" = \"android.settings.APPLICATION_DETAILS_SETTINGS\" ]; then
                        am start -a \"\$act\" -d \"package:$PKG\" >/dev/null 2>&1 || true
                    else
                        am start -a \"\$act\" >/dev/null 2>&1 || true
                    fi
                    sleep 1.0
                    focus=\$(dumpsys window 2>/dev/null | awk '/mCurrentFocus=/{line=\$0} END{print line}' | tr -d '\r')
                    echo \"  \$act -> \$focus\"
                done
            "

            # Light & Dark setup screenshots
            run_shell "$serial" "cmd uimode night no >/dev/null; am start -n $PKG/.MainActivity --es open_tab setup >/dev/null; sleep 1.2"
            capture_shot "$serial" "${safe_serial}_light_setup"

            run_shell "$serial" "cmd uimode night yes >/dev/null; am start -n $PKG/.MainActivity --es open_tab setup >/dev/null; sleep 1.2"
            capture_shot "$serial" "${safe_serial}_dark_setup"

            # Vietnamese locale (API 33+) + 1.3x font scale
            run_shell "$serial" "
                if [ '${orig_sdk:-0}' -ge 33 ]; then
                    cmd locale set-app-locales $PKG --locales vi >/dev/null 2>&1 || true
                fi
                settings put system font_scale 1.3 >/dev/null
                am start -n $PKG/.MainActivity --es open_tab home >/dev/null
                sleep 1.3
            "
            capture_shot "$serial" "${safe_serial}_vi_font13_home"

            run_shell "$serial" "am start -n $PKG/.MainActivity --es open_tab setup >/dev/null; sleep 1.2"
            capture_shot "$serial" "${safe_serial}_vi_font13_setup"

            restore_device_ui "$serial" "$orig_night" "$orig_font_scale" "$orig_sdk" "$orig_locales"

            # Debug preview matrix (only when installed package is DEBUGGABLE)
            if echo "$pkg_flags" | grep -q "DEBUGGABLE"; then
                echo "DEBUGGABLE build detected: running debug_preview_state matrix..."
                run_shell "$serial" "am start -n $PKG/.MainActivity --es open_tab home --es debug_preview_state dev_off --es debug_brand samsung >/dev/null; sleep 1.2"
                capture_shot "$serial" "${safe_serial}_preview_dev_off_samsung"

                run_shell "$serial" "am start -n $PKG/.MainActivity --es open_tab home --es debug_preview_state blocked >/dev/null; sleep 1.2"
                capture_shot "$serial" "${safe_serial}_preview_blocked"
            fi

            run_shell "$serial" "am start -n $PKG/.MainActivity --es open_tab home >/dev/null; sleep 0.8"
            ext_ok=1
        } || true

        restore_device_ui "$serial" "$orig_night" "$orig_font_scale" "$orig_sdk" "$orig_locales"
        if [[ "$ext_ok" -ne 1 ]]; then
            echo "EXTENDED CHECK ERROR on $serial" >&2
            failures=$((failures + 1))
        fi
    fi

    crash_log=$(run_shell "$serial" "logcat -d -s AndroidRuntime:E | grep -E 'FATAL EXCEPTION|$PKG' || true")

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
