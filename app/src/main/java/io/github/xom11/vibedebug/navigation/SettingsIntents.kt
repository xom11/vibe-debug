package io.github.xom11.vibedebug.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

object SettingsIntents {

    const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    const val ARG_BUILD_NUMBER = "build_number"
    const val ARG_ENABLE_ADB = "enable_adb"
    const val ARG_ENABLE_WIRELESS_ADB = "toggle_adb_wireless"

    const val ACTION_MIUI_APP_PERM_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    const val EXTRA_MIUI_PKGNAME = "extra_pkgname"
    const val MIUI_SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    const val MIUI_PERM_EDITOR_ACTIVITY = "com.miui.permcenter.permissions.PermissionsEditorActivity"

    const val ADB_GRANT_COMMAND =
        "adb shell pm grant io.github.xom11.vibedebug android.permission.WRITE_SECURE_SETTINGS"

    const val ADB_REINSTALL_GRANT_COMMAND =
        "adb shell pm path io.github.xom11.vibedebug | tr -d '\\r' | sed 's/package://' | xargs -n1 adb pull && adb install-multiple -r -g *.apk"

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

    fun buildEnableWirelessAdbIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            applyFragmentHighlight(ARG_ENABLE_WIRELESS_ADB)
        }
    }

    fun buildDeveloperOptionsTopIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }

    fun buildRootSettingsFallbackIntent(): Intent {
        return Intent(Settings.ACTION_SETTINGS)
    }

    fun buildHomeLauncherIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    }

    fun buildUsageAccessAppIntent(packageName: String): Intent {
        return Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    }

    fun buildUsageAccessFallbackIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun buildMiuiAppPermEditorIntent(packageName: String): Intent {
        return Intent(ACTION_MIUI_APP_PERM_EDITOR).apply {
            putExtra(EXTRA_MIUI_PKGNAME, packageName)
        }
    }

    fun buildMiuiAppPermEditorExplicitIntent(packageName: String): Intent {
        return Intent(ACTION_MIUI_APP_PERM_EDITOR).apply {
            setClassName(MIUI_SECURITY_CENTER_PACKAGE, MIUI_PERM_EDITOR_ACTIVITY)
            putExtra(EXTRA_MIUI_PKGNAME, packageName)
        }
    }

    fun buildAppDetailsIntent(packageName: String): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    }

    fun hasMiuiPermissionEditor(context: Context): Boolean {
        val pm = context.packageManager
        val intent = buildMiuiAppPermEditorIntent(context.packageName)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L)) != null
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0) != null
            }
        }.getOrDefault(false)
    }

    fun launchHomeShortcutPermissionScreen(context: Context): Boolean {
        val pkg = context.packageName
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                { buildMiuiAppPermEditorIntent(pkg) },
                { buildMiuiAppPermEditorExplicitIntent(pkg) },
                { buildAppDetailsIntent(pkg) },
                ::buildRootSettingsFallbackIntent
            )
        )
    }

    fun launchUsageAccessScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                { buildUsageAccessAppIntent(context.packageName) },
                ::buildUsageAccessFallbackIntent,
                ::buildRootSettingsFallbackIntent
            )
        )
    }

    fun launchHomeScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(::buildHomeLauncherIntent)
        )
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

    fun launchEnableWirelessAdbScreen(context: Context): Boolean {
        return launchWithFallbacks(
            context = context,
            candidates = listOf(
                ::buildEnableWirelessAdbIntent,
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
