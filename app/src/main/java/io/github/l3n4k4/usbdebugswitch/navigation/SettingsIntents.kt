package io.github.l3n4k4.usbdebugswitch.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

object SettingsIntents {

    const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    const val ARG_BUILD_NUMBER = "build_number"
    const val ARG_ENABLE_ADB = "enable_adb"

    const val ADB_GRANT_COMMAND =
        "adb shell pm grant io.github.l3n4k4.usbdebugswitch android.permission.WRITE_SECURE_SETTINGS"

    fun buildBuildNumberIntent(): Intent {
        return Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
            applyFragmentHighlight(ARG_BUILD_NUMBER)
        }
    }

    fun buildAboutDeviceFallbackIntent(): Intent {
        return Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
    }

    fun buildEnableAdbIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            applyFragmentHighlight(ARG_ENABLE_ADB)
        }
    }

    fun buildDeveloperOptionsTopIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }

    fun buildRootSettingsFallbackIntent(): Intent {
        return Intent(Settings.ACTION_SETTINGS)
    }

    fun launchBuildNumberScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                ::buildBuildNumberIntent,
                ::buildAboutDeviceFallbackIntent,
                ::buildRootSettingsFallbackIntent
            )
        )
    }

    fun launchEnableAdbScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                ::buildEnableAdbIntent,
                ::buildDeveloperOptionsTopIntent,
                ::buildRootSettingsFallbackIntent
            )
        )
    }

    fun launchDeveloperOptionsTopScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                ::buildDeveloperOptionsTopIntent,
                ::buildRootSettingsFallbackIntent
            )
        )
    }

    private fun Intent.applyFragmentHighlight(argKey: String) {
        putExtra(EXTRA_FRAGMENT_ARG_KEY, argKey)
        putExtra(
            EXTRA_SHOW_FRAGMENT_ARGS,
            Bundle().apply {
                putString(EXTRA_FRAGMENT_ARG_KEY, argKey)
            }
        )
    }

    private fun launchWithFallbacks(
        context: Context,
        candidates: List<() -> Intent>
    ): Boolean {
        for (builder in candidates) {
            val intent = builder().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }
}
