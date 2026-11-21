package io.github.l3n4k4.usbdebugswitch.data

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoJumpToUsbDebugging: Boolean
        get() = prefs.getBoolean(KEY_AUTO_JUMP_USB_DEBUG, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_JUMP_USB_DEBUG, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "usb_debug_switch_prefs"
        private const val KEY_AUTO_JUMP_USB_DEBUG = "auto_jump_usb_debug"
    }
}
