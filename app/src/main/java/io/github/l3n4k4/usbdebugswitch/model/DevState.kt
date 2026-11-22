package io.github.l3n4k4.usbdebugswitch.model

data class DevState(
    val devOptionsEnabled: Boolean,
    val adbEnabled: Boolean,
    val wirelessAdbEnabled: Boolean,
    val keepAwakeEnabled: Boolean = false,
    val supportsWirelessAdb: Boolean = true,
    val blockedByPolicy: Boolean = false
)

enum class OemBrand(val id: String) {
    SAMSUNG("samsung"),
    ONEPLUS_OPPO_REALME("oneplus"),
    XIAOMI("xiaomi"),
    VIVO("vivo"),
    GOOGLE_OTHER("google")
}

object OemBrandDetector {

    fun detect(manufacturer: String?, brand: String?): OemBrand {
        val candidates = listOfNotNull(manufacturer, brand)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        for (value in candidates) {
            when {
                value.contains("samsung") -> return OemBrand.SAMSUNG
                value.contains("oneplus") ||
                    value.contains("oppo") ||
                    value.contains("realme") -> return OemBrand.ONEPLUS_OPPO_REALME
                value.contains("xiaomi") ||
                    value.contains("redmi") ||
                    value.contains("poco") -> return OemBrand.XIAOMI
                value.contains("vivo") ||
                    value.contains("iqoo") -> return OemBrand.VIVO
                value.contains("google") ||
                    value.contains("pixel") -> return OemBrand.GOOGLE_OTHER
            }
        }
        return OemBrand.GOOGLE_OTHER
    }

    fun fromPreviewKey(raw: String?): OemBrand? {
        val normalized = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (normalized) {
            "samsung" -> OemBrand.SAMSUNG
            "oneplus", "oppo", "realme" -> OemBrand.ONEPLUS_OPPO_REALME
            "xiaomi", "redmi", "poco" -> OemBrand.XIAOMI
            "vivo", "iqoo" -> OemBrand.VIVO
            "google", "pixel", "other", "stock" -> OemBrand.GOOGLE_OTHER
            else -> null
        }
    }
}
