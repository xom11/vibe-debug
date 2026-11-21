package io.github.l3n4k4.usbdebugswitch

import android.provider.Settings
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents
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
}
