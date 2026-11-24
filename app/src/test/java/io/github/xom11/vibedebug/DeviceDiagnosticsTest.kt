package io.github.xom11.vibedebug

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.xom11.vibedebug.data.DeviceDiagnostics
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 31, 33, 34, 35])
class DeviceDiagnosticsTest {

    @Test
    fun `buildReport emits non-PII diagnostics across Android API 26 to 35`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val report = DeviceDiagnostics.buildReport(context)

        assertTrue(report.startsWith("Vibe Debug Diagnostics"))
        assertTrue(report.contains("API ${Build.VERSION.SDK_INT}"))
        assertTrue(report.contains("supported=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.R}"))
        assertTrue(report.contains("WRITE_SECURE_SETTINGS="))
        assertTrue(report.contains("launcher="))
        assertTrue(report.contains("intents:"))
    }

    @Test
    @Config(sdk = [34])
    fun `formatMiuiOpMode formats all known and unknown AppOps modes`() {
        assertEquals("n/a", DeviceDiagnostics.formatMiuiOpMode(null))
        assertEquals("allow(0)", DeviceDiagnostics.formatMiuiOpMode(AppOpsManager.MODE_ALLOWED))
        assertEquals("ignore(1)", DeviceDiagnostics.formatMiuiOpMode(AppOpsManager.MODE_IGNORED))
        assertEquals("error(2)", DeviceDiagnostics.formatMiuiOpMode(AppOpsManager.MODE_ERRORED))
        assertEquals("default(3)", DeviceDiagnostics.formatMiuiOpMode(AppOpsManager.MODE_DEFAULT))
        assertEquals("ask(5)", DeviceDiagnostics.formatMiuiOpMode(QuickSurfaceHelper.MIUI_MODE_ASK))
        assertEquals("mode(99)", DeviceDiagnostics.formatMiuiOpMode(99))
    }
}
