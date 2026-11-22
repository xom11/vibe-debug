package io.github.l3n4k4.usbdebugswitch.debug

import android.content.Intent
import android.os.Build
import io.github.l3n4k4.usbdebugswitch.BuildConfig
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.model.OemBrandDetector

/** Debug-only preview configuration parsed from launcher intent extras. */
data class DebugPreviewConfig(
    val stateKey: String,
    val simulatedState: DevState,
    val hasWritePermission: Boolean,
    val brandOverride: OemBrand?,
    val initialTab: String? = null
) {
    val bannerText: String
        get() = buildString {
            append("DEBUG PREVIEW • state=")
            append(stateKey)
            brandOverride?.let {
                append(" • brand=")
                append(it.id)
            }
            initialTab?.let {
                append(" • tab=")
                append(it)
            }
        }

    companion object {
        const val EXTRA_PREVIEW_STATE = "debug_preview_state"
        const val EXTRA_PREVIEW_BRAND = "debug_brand"
        const val EXTRA_PREVIEW_TAB = "debug_tab"

        private val VALID_TABS = setOf("home", "shortcuts", "setup")

        fun fromIntent(intent: Intent?): DebugPreviewConfig? {
            if (!BuildConfig.DEBUG || intent == null) return null

            val rawState = intent.getStringExtra(EXTRA_PREVIEW_STATE)
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            val supportsWireless = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val brandOverride = OemBrandDetector.fromPreviewKey(
                intent.getStringExtra(EXTRA_PREVIEW_BRAND)
            )
            val initialTab = intent.getStringExtra(EXTRA_PREVIEW_TAB)
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in VALID_TABS }

            val preset: Pair<DevState, Boolean> = when (rawState) {
                "dev_off" -> DevState(
                    devOptionsEnabled = false,
                    adbEnabled = false,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless
                ) to false

                "adb_off" -> DevState(
                    devOptionsEnabled = true,
                    adbEnabled = false,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless
                ) to false

                "adb_on" -> DevState(
                    devOptionsEnabled = true,
                    adbEnabled = true,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless
                ) to false

                "privileged" -> DevState(
                    devOptionsEnabled = true,
                    adbEnabled = true,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless
                ) to true

                "privileged_off" -> DevState(
                    devOptionsEnabled = true,
                    adbEnabled = false,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless
                ) to true

                "blocked" -> DevState(
                    devOptionsEnabled = false,
                    adbEnabled = false,
                    wirelessAdbEnabled = false,
                    supportsWirelessAdb = supportsWireless,
                    blockedByPolicy = true
                ) to false

                else -> return null
            }

            return DebugPreviewConfig(
                stateKey = rawState,
                simulatedState = preset.first,
                hasWritePermission = preset.second,
                brandOverride = brandOverride,
                initialTab = initialTab
            )
        }
    }
}
