package io.github.l3n4k4.usbdebugswitch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.l3n4k4.usbdebugswitch.data.AppPreferences
import io.github.l3n4k4.usbdebugswitch.data.AutoOffManager
import io.github.l3n4k4.usbdebugswitch.data.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoOffAndPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private lateinit var autoOffManager: AutoOffManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("usb_debug_switch_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = AppPreferences(context)
        autoOffManager = AutoOffManager(context, prefs)
    }

    @Test
    fun `themePreference defaults to SYSTEM and resolves keys safely`() {
        assertEquals(ThemePreference.SYSTEM, prefs.themePreference)
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey("unknown"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromKey("dark"))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromKey("light"))

        prefs.themePreference = ThemePreference.DARK
        assertEquals(ThemePreference.DARK, AppPreferences(context).themePreference)

        prefs.themePreference = ThemePreference.LIGHT
        assertEquals(ThemePreference.LIGHT, AppPreferences(context).themePreference)
    }

    @Test
    fun `safeLaunchPackages maintains deduplicated ordered list across reloads`() {
        assertTrue(prefs.safeLaunchPackages.isEmpty())

        prefs.addSafeLaunchPackage("com.VCB")
        prefs.addSafeLaunchPackage("com.vnid")
        prefs.addSafeLaunchPackage("com.VCB")
        prefs.addSafeLaunchPackage("   ")

        assertEquals(listOf("com.VCB", "com.vnid"), prefs.safeLaunchPackages)
        assertEquals(listOf("com.VCB", "com.vnid"), AppPreferences(context).safeLaunchPackages)

        prefs.removeSafeLaunchPackage("com.VCB")
        assertEquals(listOf("com.vnid"), AppPreferences(context).safeLaunchPackages)
    }

    @Test
    fun `autoOffMinutes validates supported presets and defaults to 30`() {
        assertFalse(prefs.autoOffEnabled)
        assertEquals(30, prefs.autoOffMinutes)

        prefs.autoOffMinutes = 15
        assertEquals(15, prefs.autoOffMinutes)

        prefs.autoOffMinutes = 60
        assertEquals(60, prefs.autoOffMinutes)

        prefs.autoOffMinutes = 999
        assertEquals(30, prefs.autoOffMinutes)
    }

    @Test
    fun `AutoOffManager start remainingMinutes cancel and resync manage deadline cleanly`() {
        assertNull(autoOffManager.remainingMinutes())

        val beforeStart = System.currentTimeMillis()
        autoOffManager.start(30)
        assertTrue(prefs.autoOffTargetAtMs >= beforeStart + 30 * 60_000L)

        val remaining = autoOffManager.remainingMinutes(prefs.autoOffTargetAtMs - 15 * 60_000L)
        assertEquals(15, remaining)
        assertNull(autoOffManager.remainingMinutes(prefs.autoOffTargetAtMs))

        autoOffManager.resync(usbEnabled = true)
        assertNotNull(autoOffManager.remainingMinutes(prefs.autoOffTargetAtMs - 60_000L))

        autoOffManager.resync(usbEnabled = false)
        assertEquals(0L, prefs.autoOffTargetAtMs)
        assertNull(autoOffManager.remainingMinutes())

        autoOffManager.start(15)
        assertTrue(prefs.autoOffTargetAtMs > 0L)
        autoOffManager.cancel()
        assertEquals(0L, prefs.autoOffTargetAtMs)
        assertNull(autoOffManager.remainingMinutes())

        autoOffManager.start(0)
        assertEquals(0L, prefs.autoOffTargetAtMs)
    }

    @Test
    fun `AutoOffManager scheduleIfEnabled respects autoOffEnabled and USB state`() {
        prefs.autoOffEnabled = false
        prefs.autoOffMinutes = 15
        autoOffManager.scheduleIfEnabled(usbEnabled = true)
        assertEquals(0L, prefs.autoOffTargetAtMs)

        prefs.autoOffEnabled = true
        autoOffManager.scheduleIfEnabled(usbEnabled = true)
        assertTrue(prefs.autoOffTargetAtMs > System.currentTimeMillis())

        autoOffManager.scheduleIfEnabled(usbEnabled = false)
        assertEquals(0L, prefs.autoOffTargetAtMs)
    }
}
