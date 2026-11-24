package io.github.xom11.vibedebug.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.navigation.SettingsIntents

/**
 * Trampoline activity for Safe Launch home shortcuts. Restricts execution to packages
 * explicitly saved by the user in [AppPreferences.safeLaunchPackages].
 */
class SafeLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent?.getStringExtra(SafeLaunchHelper.EXTRA_PACKAGE)?.trim()
        val allowlist = AppPreferences(applicationContext).safeLaunchPackages
        if (pkg.isNullOrEmpty() || pkg !in allowlist) {
            showToast(getString(R.string.safe_launch_not_listed))
            finish()
            return
        }

        val label = SafeLaunchHelper.getAppLabel(this, pkg)
        when (SafeLaunchHelper.launchSafely(this, pkg)) {
            SafeLaunchResult.LaunchedClean -> {
                showToast(getString(R.string.safe_launch_toast_clean, label))
            }

            SafeLaunchResult.NeedsPermission -> {
                showToast(getString(R.string.safe_launch_toast_need_perm))
                val devEnabled = DevSettingsController(AndroidSettingsGateway(applicationContext))
                    .readState()
                    .devOptionsEnabled
                if (devEnabled) {
                    SettingsIntents.launchDeveloperOptionsTopScreen(this)
                } else {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    )
                }
            }

            SafeLaunchResult.BlockedByPolicy -> {
                showToast(getString(R.string.safe_launch_toast_blocked))
            }

            SafeLaunchResult.AppNotFound -> {
                showToast(getString(R.string.safe_launch_toast_not_found))
            }
        }

        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
