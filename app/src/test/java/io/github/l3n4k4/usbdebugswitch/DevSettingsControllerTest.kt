package io.github.l3n4k4.usbdebugswitch

import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.data.SettingsGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevSettingsControllerTest {

    private class FakeSettingsGateway(
        override var supportsWirelessAdb: Boolean = true,
        var hasPermission: Boolean = true,
        var devOptions: Boolean = false,
        var adb: Boolean = false,
        var wirelessAdb: Boolean = false
    ) : SettingsGateway {
        val writeLog = mutableListOf<Pair<String, Boolean>>()

        override fun hasWriteSecureSettings(): Boolean = hasPermission
        override fun isDevOptionsEnabled(): Boolean = devOptions
        override fun isAdbEnabled(): Boolean = adb
        override fun isWirelessAdbEnabled(): Boolean = wirelessAdb

        override fun putDevOptionsEnabled(enabled: Boolean): Boolean {
            if (!hasPermission) return false
            devOptions = enabled
            writeLog += "development_settings_enabled" to enabled
            return true
        }

        override fun putAdbEnabled(enabled: Boolean): Boolean {
            if (!hasPermission) return false
            adb = enabled
            writeLog += "adb_enabled" to enabled
            return true
        }

        override fun putWirelessAdbEnabled(enabled: Boolean): Boolean {
            if (!hasPermission || !supportsWirelessAdb) return false
            wirelessAdb = enabled
            writeLog += "adb_wifi_enabled" to enabled
            return true
        }
    }

    @Test
    fun `enabling USB debugging when developer options is OFF auto-enables developer options first`() {
        val gateway = FakeSettingsGateway(
            hasPermission = true,
            devOptions = false,
            adb = false,
            wirelessAdb = false
        )
        val controller = DevSettingsController(gateway)

        assertTrue(controller.setUsbDebugging(true))
        assertEquals(
            listOf(
                "development_settings_enabled" to true,
                "adb_enabled" to true
            ),
            gateway.writeLog
        )
        val state = controller.readState()
        assertTrue(state.devOptionsEnabled)
        assertTrue(state.adbEnabled)
    }

    @Test
    fun `enabling wireless debugging when developer options is OFF auto-enables developer options first`() {
        val gateway = FakeSettingsGateway(
            supportsWirelessAdb = true,
            hasPermission = true,
            devOptions = false,
            adb = false,
            wirelessAdb = false
        )
        val controller = DevSettingsController(gateway)

        assertTrue(controller.setWirelessDebugging(true))
        assertEquals(
            listOf(
                "development_settings_enabled" to true,
                "adb_wifi_enabled" to true
            ),
            gateway.writeLog
        )
        assertTrue(controller.readState().wirelessAdbEnabled)
    }

    @Test
    fun `turning off developer options cascades off USB and wireless debugging`() {
        val gateway = FakeSettingsGateway(
            supportsWirelessAdb = true,
            hasPermission = true,
            devOptions = true,
            adb = true,
            wirelessAdb = true
        )
        val controller = DevSettingsController(gateway)

        assertTrue(controller.setDeveloperOptions(false))
        assertEquals(
            listOf(
                "adb_enabled" to false,
                "adb_wifi_enabled" to false,
                "development_settings_enabled" to false
            ),
            gateway.writeLog
        )
        val state = controller.readState()
        assertFalse(state.devOptionsEnabled)
        assertFalse(state.adbEnabled)
        assertFalse(state.wirelessAdbEnabled)
    }

    @Test
    fun `turning off developer options on pre-Android 11 skips wireless debugging write`() {
        val gateway = FakeSettingsGateway(
            supportsWirelessAdb = false,
            hasPermission = true,
            devOptions = true,
            adb = true,
            wirelessAdb = false
        )
        val controller = DevSettingsController(gateway)

        assertTrue(controller.setDeveloperOptions(false))
        assertEquals(
            listOf(
                "adb_enabled" to false,
                "development_settings_enabled" to false
            ),
            gateway.writeLog
        )
    }

    @Test
    fun `unprivileged mode blocks all write operations safely`() {
        val gateway = FakeSettingsGateway(
            hasPermission = false,
            devOptions = true,
            adb = true
        )
        val controller = DevSettingsController(gateway)

        assertFalse(controller.setUsbDebugging(false))
        assertFalse(controller.setWirelessDebugging(true))
        assertFalse(controller.setDeveloperOptions(false))
        assertTrue(gateway.writeLog.isEmpty())
    }

    @Test
    fun `toggleUsbDebugging flips state and auto-enables dev options when turning ON`() {
        val gateway = FakeSettingsGateway(
            hasPermission = true,
            devOptions = false,
            adb = false
        )
        val controller = DevSettingsController(gateway)

        assertTrue(controller.toggleUsbDebugging())
        assertTrue(gateway.devOptions)
        assertTrue(gateway.adb)

        assertTrue(controller.toggleUsbDebugging())
        assertTrue(gateway.devOptions)
        assertFalse(gateway.adb)
    }
}
