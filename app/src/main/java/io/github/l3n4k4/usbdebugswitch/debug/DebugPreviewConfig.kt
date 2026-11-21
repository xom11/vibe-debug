package io.github.l3n4k4.usbdebugswitch.debug

import android.content.Intent
import android.os.Build
import io.github.l3n4k4.usbdebugswitch.BuildConfig
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.model.OemBrandDetector

data class DebugPreviewConfig(
    val stateKey: String,
    val simulatedState: DevState,
    val hasWritePermission: Boolean,
    val brandOverride: OemBrand?
) {
    val bannerText: String
        get() = buildString {
            append("DEBUG PREVIEW • state=")
            append(stateKey)
            brandOverride?.let {
                append(" • brand=")
                append(it.id)
            }
        }

    companion object {
        const val EXTRA_PREVIEW_STATE = "debug_preview_state"
        const val EXTRA_PREVIEW_BRAND = "debug_brand"

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

            return when (rawState) {
                "dev_off" -> DebugPreviewConfig(
                    stateKey = "dev_off",
                    simulatedState = DevState(
                        devOptionsEnabled = false,
                        adbEnabled = false,
                        wirelessAdbEnabled = false,
                        supportsWirelessAdb = supportsWireless
                    ),
                    hasWritePermission = false,
                    brandOverride = brandOverride
                )

                "adb_off" -> DebugPreviewConfig(
                    stateKey = "adb_off",
                    simulatedState = DevState(
                        devOptionsEnabled = true,
                        adbEnabled = false,
                        wirelessAdbEnabled = false,
                        supportsWirelessAdb = supportsWireless
                    ),
                    hasWritePermission = false,
                    brandOverride = brandOverride
                )

                "adb_on" -> DebugPreviewConfig(
                    stateKey = "adb_on",
                    simulatedState = DevState(
                        devOptionsEnabled = true,
                        adbEnabled = true,
                        wirelessAdbEnabled = false,
                        supportsWirelessAdb = supportsWireless
                    ),
                    hasWritePermission = false,
                    brandOverride = brandOverride
                )

                "privileged" -> DebugPreviewConfig(
                    stateKey = "privileged",
                    simulatedState = DevState(
                        devOptionsEnabled = true,
                        adbEnabled = true,
                        wirelessAdbEnabled = false,
                        supportsWirelessAdb = supportsWireless
                    ),
                    hasWritePermission = true,
                    brandOverride = brandOverride
                )

                else -> null
            }
        }
    }
}
