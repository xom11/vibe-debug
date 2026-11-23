package io.github.xom11.vibedebug.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.navigation.SettingsIntents

class ShortcutDispatchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = DevSettingsController(AndroidSettingsGateway(applicationContext))
        val state = controller.readState()
        val hasPerm = controller.hasWritePermission()

        val blockedOrNeedsMain = state.blockedByPolicy || hasPerm || !state.devOptionsEnabled

        when (intent?.action) {
            ACTION_SHORTCUT_USB -> {
                if (blockedOrNeedsMain) {
                    openMainApp()
                } else {
                    SettingsIntents.launchEnableAdbScreen(this)
                }
            }

            ACTION_SHORTCUT_WIRELESS -> {
                if (blockedOrNeedsMain || !state.supportsWirelessAdb) {
                    openMainApp()
                } else {
                    SettingsIntents.launchEnableWirelessAdbScreen(this)
                }
            }

            ACTION_SHORTCUT_DEV_TOP -> {
                if (blockedOrNeedsMain) {
                    openMainApp()
                } else {
                    SettingsIntents.launchDeveloperOptionsTopScreen(this)
                }
            }

            else -> openMainApp()
        }

        finish()
    }

    private fun openMainApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    companion object {
        const val ACTION_SHORTCUT_USB = "io.github.xom11.vibedebug.SHORTCUT_USB_DEBUG"
        const val ACTION_SHORTCUT_WIRELESS = "io.github.xom11.vibedebug.SHORTCUT_WIRELESS_DEBUG"
        const val ACTION_SHORTCUT_DEV_TOP = "io.github.xom11.vibedebug.SHORTCUT_DEV_TOP"
    }
}
