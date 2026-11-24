package io.github.xom11.vibedebug

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xom11.vibedebug.data.DeviceDiagnostics
import io.github.xom11.vibedebug.debug.DebugPreviewConfig
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.widget.UsbDebugWidgetProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun modeA_tabNavigation_andCopyDiagnostics_workEndToEnd() {
        val intent = Intent(context(), MainActivity::class.java).apply {
            putExtra(DebugPreviewConfig.EXTRA_PREVIEW_STATE, "adb_off")
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_tab_shortcuts").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_tab_setup").assertIsDisplayed()
            composeRule.onNodeWithTag("card_usb_hero").assertIsDisplayed()

            composeRule.onNodeWithTag("nav_tab_shortcuts").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_qs_tiles").assertIsDisplayed()
            composeRule.onNodeWithTag("card_widget_hub").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag("nav_tab_setup").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_setup_checklist").assertIsDisplayed()

            composeRule.onNodeWithTag("btn_copy_diagnostics")
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()

            var clipText: String? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val cm = context().getSystemService(ClipboardManager::class.java)
                clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context())?.toString()
            }
            assertNotNull("Expected clipboard diagnostics text", clipText)
            assertTrue(clipText!!.startsWith("Vibe Debug Diagnostics"))
            assertTrue(clipText!!.contains("API ${Build.VERSION.SDK_INT}"))
        }
    }

    @Test
    fun previewAllStatesAndOemBrands_renderWithoutCrash() {
        val scenarios = listOf(
            "dev_off" to "samsung",
            "dev_off" to "xiaomi",
            "dev_off" to "oneplus",
            "dev_off" to "vivo",
            "dev_off" to "google",
            "adb_on" to null,
            "privileged" to null,
            "blocked" to null,
        )

        scenarios.forEach { (state, brand) ->
            val intent = Intent(context(), MainActivity::class.java).apply {
                putExtra(DebugPreviewConfig.EXTRA_PREVIEW_STATE, state)
                if (brand != null) {
                    putExtra(DebugPreviewConfig.EXTRA_PREVIEW_BRAND, brand)
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("debug_preview_banner").assertIsDisplayed()
                composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
            }
        }
    }

    @Test
    fun repeatedOpenTabIntent_updatesActiveTabOnRunningActivity() {
        val initialIntent = Intent(context(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, "shortcuts")
        }

        ActivityScenario.launch<MainActivity>(initialIntent).use { scenario ->
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_qs_tiles").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_TAB, "setup")
                    }
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_setup_checklist").assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_TAB, "home")
                    }
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_usb_hero").assertIsDisplayed()
        }
    }

    @Test
    fun diagnosticsWidgetAndSurfaces_workOnRealRuntime() {
        val ctx = context()
        val report = DeviceDiagnostics.buildReport(ctx)
        assertTrue(report.startsWith("Vibe Debug Diagnostics"))
        assertTrue(report.contains("API ${Build.VERSION.SDK_INT}"))

        val surfaceState = QuickSurfaceHelper.readSurfaceState(ctx)
        assertNotNull(surfaceState)

        val remoteViews = UsbDebugWidgetProvider().buildRemoteViews(ctx, appWidgetId = 1)
        assertNotNull(remoteViews)

        val am = ctx.getSystemService(ActivityManager::class.java)
        assertFalse(am == null)
    }
}
