package io.github.xom11.vibedebug

import android.provider.Settings
import io.github.xom11.vibedebug.navigation.SettingsIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsIntentsTest {

    @Test
    fun `buildEnableAdbIntent targets Developer Options with enable_adb highlight extras`() {
        val intent = SettingsIntents.buildEnableAdbIntent()

        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, intent.action)
        assertEquals(
            SettingsIntents.ARG_ENABLE_ADB,
            intent.getStringExtra(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
        val bundle = intent.getBundleExtra(SettingsIntents.EXTRA_SHOW_FRAGMENT_ARGS)
        assertNotNull(bundle)
        assertEquals(
            SettingsIntents.ARG_ENABLE_ADB,
            bundle?.getString(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
    }

    @Test
    fun `buildBuildNumberIntent targets Device Info with build_number highlight extras`() {
        val intent = SettingsIntents.buildBuildNumberIntent()

        assertEquals(Settings.ACTION_DEVICE_INFO_SETTINGS, intent.action)
        assertEquals(
            SettingsIntents.ARG_BUILD_NUMBER,
            intent.getStringExtra(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
        val bundle = intent.getBundleExtra(SettingsIntents.EXTRA_SHOW_FRAGMENT_ARGS)
        assertNotNull(bundle)
        assertEquals(
            SettingsIntents.ARG_BUILD_NUMBER,
            bundle?.getString(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
    }

    @Test
    fun `buildEnableWirelessAdbIntent targets Developer Options with toggle_adb_wireless highlight extras`() {
        val intent = SettingsIntents.buildEnableWirelessAdbIntent()

        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, intent.action)
        assertEquals(
            SettingsIntents.ARG_ENABLE_WIRELESS_ADB,
            intent.getStringExtra(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
        val bundle = intent.getBundleExtra(SettingsIntents.EXTRA_SHOW_FRAGMENT_ARGS)
        assertNotNull(bundle)
        assertEquals(
            SettingsIntents.ARG_ENABLE_WIRELESS_ADB,
            bundle?.getString(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY)
        )
    }

    @Test
    fun `buildDeveloperOptionsTopIntent omits fragment highlight args for top-of-page Banking helper`() {
        val intent = SettingsIntents.buildDeveloperOptionsTopIntent()

        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, intent.action)
        assertNull(intent.getStringExtra(SettingsIntents.EXTRA_FRAGMENT_ARG_KEY))
        assertNull(intent.getBundleExtra(SettingsIntents.EXTRA_SHOW_FRAGMENT_ARGS))
    }

    @Test
    fun `ADB_REINSTALL_GRANT_COMMAND preserves clean reinstall grant command`() {
        assertEquals(
            "adb shell pm path io.github.xom11.vibedebug | tr -d '\\r' | sed 's/package://' | xargs -n1 adb pull && adb install-multiple -r -g *.apk",
            SettingsIntents.ADB_REINSTALL_GRANT_COMMAND
        )
    }

    @Test
    fun `buildUsageAccess intents target usage access settings with and without package Uri`() {
        val appIntent = SettingsIntents.buildUsageAccessAppIntent("io.github.xom11.vibedebug")
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, appIntent.action)
        assertEquals("package:io.github.xom11.vibedebug", appIntent.dataString)

        val fallbackIntent = SettingsIntents.buildUsageAccessFallbackIntent()
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, fallbackIntent.action)
        assertNull(fallbackIntent.data)
    }

    @Test
    fun `buildMiuiAppPermEditor and app details intents carry target package extras`() {
        val implicit = SettingsIntents.buildMiuiAppPermEditorIntent("io.github.xom11.vibedebug")
        assertEquals(SettingsIntents.ACTION_MIUI_APP_PERM_EDITOR, implicit.action)
        assertEquals(
            "io.github.xom11.vibedebug",
            implicit.getStringExtra(SettingsIntents.EXTRA_MIUI_PKGNAME)
        )
        assertNull(implicit.component)

        val explicit = SettingsIntents.buildMiuiAppPermEditorExplicitIntent("io.github.xom11.vibedebug")
        assertEquals(SettingsIntents.ACTION_MIUI_APP_PERM_EDITOR, explicit.action)
        assertEquals(
            SettingsIntents.MIUI_SECURITY_CENTER_PACKAGE,
            explicit.component?.packageName
        )
        assertEquals(
            SettingsIntents.MIUI_PERM_EDITOR_ACTIVITY,
            explicit.component?.className
        )
        assertEquals(
            "io.github.xom11.vibedebug",
            explicit.getStringExtra(SettingsIntents.EXTRA_MIUI_PKGNAME)
        )

        val details = SettingsIntents.buildAppDetailsIntent("io.github.xom11.vibedebug")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
        assertEquals("package:io.github.xom11.vibedebug", details.dataString)
    }
}
