package io.github.xom11.vibedebug

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.rules.ApkStructureSignals
import io.github.xom11.vibedebug.rules.AsciiPatternMatcher
import io.github.xom11.vibedebug.rules.DetectionSource
import io.github.xom11.vibedebug.rules.DexAntiDebugSignals
import io.github.xom11.vibedebug.rules.StrictAppCatalog
import io.github.xom11.vibedebug.rules.StrictAppCategory
import io.github.xom11.vibedebug.rules.StrictAppConfidence
import io.github.xom11.vibedebug.rules.StrictAppDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrictAppDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("usb_debug_switch_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = AppPreferences(context)
    }

    @Test
    fun `StrictAppCatalog includes VssID VN gov apps banks wallets global apps and valid package IDs`() {
        val pkgRegex = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        assertTrue(StrictAppCatalog.KNOWN_STRICT_PACKAGES.size >= 110)
        StrictAppCatalog.KNOWN_STRICT_PACKAGES.forEach { pkg ->
            assertTrue("Invalid package ID format: $pkg", pkgRegex.matches(pkg))
        }

        // Vietnam Government / Digital ID / Public Services
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.bhxhapp"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.vnid"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.etax.icanhan"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.ots.c08.vnetraffic"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.viettel.citizens.hanoi"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("vn.stttt.hues"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("vn.com.vetc.app"))
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("com.viettel.etc.epass"))

        // Vietnam Banking & E-Wallets
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.VCB"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.vnpay.vpbankonline"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.vnpay.Agribank3g"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.vib.myvib2"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.sacombank.ewallet"))
        assertEquals(StrictAppCategory.EWALLET, StrictAppCatalog.categoryFor("com.mservice.momotransfer"))
        assertEquals(StrictAppCategory.EWALLET, StrictAppCatalog.categoryFor("vn.com.vng.zalopay"))
        assertEquals(StrictAppCategory.EWALLET, StrictAppCatalog.categoryFor("vnpay.smartacccount"))
        assertEquals(StrictAppCategory.EWALLET, StrictAppCatalog.categoryFor("com.beeasy.toppay"))
        assertEquals(StrictAppCategory.EWALLET, StrictAppCatalog.categoryFor("com.bplus.vtpay"))

        // Global strict banking / ID / crypto / enterprise
        assertEquals(StrictAppCategory.GOV_ID, StrictAppCatalog.categoryFor("sg.ndi.sp"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.revolut.revolut"))
        assertEquals(StrictAppCategory.BANKING, StrictAppCatalog.categoryFor("com.sbi.lotusintouch"))
        assertEquals(StrictAppCategory.SECURITIES_FINTECH, StrictAppCatalog.categoryFor("com.binance.dev"))
        assertEquals(
            StrictAppCategory.ENTERPRISE_AUTH,
            StrictAppCatalog.categoryFor("com.microsoft.windowsintune.companyportal")
        )
    }

    @Test
    fun `analyzeApkEntryNames detects RASP and banking security SDKs while ignoring Spotify rootChecker`() {
        val bshield = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf("classes.dex", "assets/bshield.dat", "lib/arm64-v8a/libapp.so")
        )
        assertTrue("BShield" in bshield.raspSdks)

        val dexProtector = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf("lib/arm64-v8a/libdexprotector.02i1.so", "lib/arm64-v8a/libdpboot.02i1.so")
        )
        assertTrue("DexProtector" in dexProtector.raspSdks)

        val vkey = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf("assets/vkeylicensepack", "lib/arm64-v8a/libvosWrapperEx.so")
        )
        assertTrue("V-Key" in vkey.raspSdks)

        val szipContainer = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf(
                "assets/xfxjmaqdgw2.szip",
                "assets/xfxjmaqdgw2/0.odex",
                "lib/arm64-v8a/libxfxjmaqdgw2.so",
                "lib/arm64-v8a/libTMXProfiling-rl-8.0-82-jni.so",
                "lib/arm64-v8a/libKeypassSoftTokenSdk-1.5.2.1_VPBSDK.so",
                "lib/arm64-v8a/libtoolChecker.so",
            )
        )
        assertTrue("Shield" in szipContainer.raspSdks)
        assertTrue("ThreatMetrix" in szipContainer.bankingSecuritySdks)
        assertTrue("SoftOTP" in szipContainer.bankingSecuritySdks)
        assertTrue(szipContainer.hasRootBeerNative)

        assertTrue(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/base.apk"))
        assertTrue(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/split_config.arm64_v8a.apk"))
        assertFalse(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/split_config.en.apk"))
        assertFalse(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/split_config.zh_rTW.apk"))
        assertFalse(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/split_config.xxhdpi.apk"))
        assertFalse(StrictAppDetector.isRelevantApkForStructure("/data/app/foo/split_config.nodpi.apk"))

        val commercialSdks = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf(
                "lib/arm64-v8a/libshield.so",
                "assets/appdome/config.json",
                "lib/arm64-v8a/libappsealing.so",
                "lib/arm64-v8a/libzdefend.so",
                "lib/arm64-v8a/libtalsec.so",
                "lib/arm64-v8a/libvds-sotp.so",
            )
        )
        assertEquals(
            setOf("Promon", "Appdome", "AppSealing", "Zimperium", "freeRASP"),
            commercialSdks.raspSdks
        )
        assertEquals(setOf("SoftOTP"), commercialSdks.bankingSecuritySdks)

        // Spotify includes librootChecker.so and must NOT trigger false positives.
        val spotifyLike = StrictAppDetector.analyzeApkEntryNames(
            sequenceOf(
                "classes.dex",
                "lib/arm64-v8a/libandroidx.graphics.path.so",
                "lib/arm64-v8a/librootChecker.so",
                "lib/arm64-v8a/liborbit-jni-spotify.so",
            )
        )
        assertTrue(spotifyLike.raspSdks.isEmpty())
        assertTrue(spotifyLike.bankingSecuritySdks.isEmpty())
        assertFalse(spotifyLike.hasRootBeerNative)
    }

    @Test
    fun `AsciiPatternMatcher matches across 64KiB chunk boundaries and non-ASCII bytes`() {
        val matcher = AsciiPatternMatcher(listOf("adb_enabled", "development_settings_enabled", "com/aheaditec/talsec"))
        val chunkBoundaryOffset = 64 * 1024 - 4 // "adb_" at end of chunk 1, "enabled" at start of chunk 2
        val payload = ByteArray(140 * 1024) { 0xFF.toByte() }
        "adb_enabled".encodeToByteArray().copyInto(payload, destinationOffset = chunkBoundaryOffset)
        "com/aheaditec/talsec".encodeToByteArray().copyInto(payload, destinationOffset = 90_000)

        val matched = matcher.scanStream(
            input = ByteArrayInputStream(payload),
            maxBytes = payload.size.toLong(),
        )
        assertEquals(setOf(0, 2), matched)
    }

    @Test
    fun `scanApkDex reads multi-dex ZIP archives handles bad files and respects cap and cancellation`() {
        val apkFile = tempFolder.newFile("synthetic.apk")
        ZipOutputStream(FileOutputStream(apkFile)).use { zip ->
            zip.putNextEntry(ZipEntry("assets/bshield.dat"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("random_header\u0000adb_enabled\u0000more".encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("classes2.dex"))
            zip.write("something\u0000development_settings_enabled\u0000adb_wifi_enabled".encodeToByteArray())
            zip.closeEntry()
        }

        val struct = StrictAppDetector.inspectApkStructure(listOf(apkFile.absolutePath))
        assertTrue("BShield" in struct.raspSdks)

        val dexSignals = StrictAppDetector.scanApkDex(listOf(apkFile.absolutePath))
        assertTrue(dexSignals.checksAdbEnabled)
        assertTrue(dexSignals.checksDevSettings)
        assertTrue(dexSignals.checksAdbWifi)

        val cappedSignals = StrictAppDetector.scanApkDex(
            apkPaths = listOf(apkFile.absolutePath),
            maxBytesPerApp = 5L,
        )
        assertFalse(cappedSignals.hasAnySignal)

        val cancelledSignals = StrictAppDetector.scanApkDex(
            apkPaths = listOf(apkFile.absolutePath),
            isCancelled = { true },
        )
        assertFalse(cancelledSignals.hasAnySignal)

        // Corrupt ZIP & missing file must never throw.
        val corruptFile = tempFolder.newFile("corrupt.apk")
        corruptFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val missingPath = File(tempFolder.root, "does_not_exist.apk").absolutePath
        val safeStruct = StrictAppDetector.inspectApkStructure(listOf(corruptFile.absolutePath, missingPath))
        val safeDex = StrictAppDetector.scanApkDex(listOf(corruptFile.absolutePath, missingPath))
        assertTrue(safeStruct.raspSdks.isEmpty())
        assertFalse(safeDex.hasAnySignal)
    }

    @Test
    fun `evaluateSignals scores unknown strict apps cleanly and dampens media or social telemetry false positives`() {
        // 1. Unknown app protected by BShield -> HIGH
        val unknownBshield = StrictAppDetector.evaluateSignals(
            packageName = "com.example.newbank",
            label = "New App",
            structure = ApkStructureSignals(raspSdks = setOf("BShield")),
            dex = null,
            appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
        )
        assertEquals(StrictAppConfidence.HIGH, unknownBshield.confidence)
        assertTrue(DetectionSource.RASP_SDK in unknownBshield.sources)
        assertEquals(listOf("BShield"), unknownBshield.detectedSdks)

        // 2. Unknown government/bank app checking adb + dev settings in DEX -> HIGH
        val unknownGov = StrictAppDetector.evaluateSignals(
            packageName = "vn.gov.futureapp",
            label = "Dịch vụ công mới",
            structure = ApkStructureSignals(),
            dex = DexAntiDebugSignals(checksAdbEnabled = true, checksDevSettings = true),
            appCategory = ApplicationInfo.CATEGORY_PRODUCTIVITY,
        )
        assertEquals(StrictAppConfidence.HIGH, unknownGov.confidence)
        assertTrue(DetectionSource.DOMAIN_HEURISTIC in unknownGov.sources)
        assertTrue(DetectionSource.DEX_ANTI_DEBUG in unknownGov.sources)

        // 3. Unknown non-bank app (e.g. proctoring / exam app) checking both flags -> MEDIUM
        val unknownExam = StrictAppDetector.evaluateSignals(
            packageName = "com.example.exam",
            label = "Exam Guard",
            structure = ApkStructureSignals(),
            dex = DexAntiDebugSignals(checksAdbEnabled = true, checksDevSettings = true),
            appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
        )
        assertEquals(StrictAppConfidence.MEDIUM, unknownExam.confidence)
        assertTrue(unknownExam.isSuggested)
        assertFalse(unknownExam.isHighConfidence)

        // 4. Media / Social app (Spotify/Zalo style telemetry) with no RASP SDK -> dampened to NONE
        val mediaTelemetry = StrictAppDetector.evaluateSignals(
            packageName = "com.example.musicplayer",
            label = "Music Stream",
            structure = ApkStructureSignals(),
            dex = DexAntiDebugSignals(checksAdbEnabled = true, checksDevSettings = true),
            appCategory = ApplicationInfo.CATEGORY_AUDIO,
        )
        assertEquals(StrictAppConfidence.NONE, mediaTelemetry.confidence)

        val socialTelemetry = StrictAppDetector.evaluateSignals(
            packageName = "com.example.chatapp",
            label = "Chat App",
            structure = ApkStructureSignals(),
            dex = DexAntiDebugSignals(checksAdbEnabled = false, checksDevSettings = true),
            appCategory = ApplicationInfo.CATEGORY_SOCIAL,
        )
        assertEquals(StrictAppConfidence.NONE, socialTelemetry.confidence)

        // 5. RootBeer native alone stays NONE; paired with DEX checks promotes to MEDIUM/HIGH
        val rootBeerOnly = StrictAppDetector.evaluateSignals(
            packageName = "com.example.utility",
            label = "Utility",
            structure = ApkStructureSignals(hasRootBeerNative = true),
            dex = null,
            appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
        )
        assertEquals(StrictAppConfidence.NONE, rootBeerOnly.confidence)

        val rootBeerWithDex = StrictAppDetector.evaluateSignals(
            packageName = "com.example.utility",
            label = "Utility",
            structure = ApkStructureSignals(hasRootBeerNative = true),
            dex = DexAntiDebugSignals(checksAdbEnabled = true, checksDevSettings = true),
            appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
        )
        assertEquals(StrictAppConfidence.HIGH, rootBeerWithDex.confidence)

        // 6. Strong security SDKs (ThreatMetrix, SoftOTP) sort ahead of generic Security SDK.
        val sdkOrdering = StrictAppDetector.evaluateSignals(
            packageName = "com.example.exchange",
            label = "Exchange",
            structure = ApkStructureSignals(
                bankingSecuritySdks = setOf("Security SDK", "ThreatMetrix"),
            ),
            dex = null,
            appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
        )
        assertEquals(listOf("ThreatMetrix", "Security SDK"), sdkOrdering.detectedSdks)
    }

    @Test
    fun `AppPreferences batch addSafeLaunchPackages and DEX scan cache persist and invalidate cleanly`() {
        prefs.addSafeLaunchPackage("com.VCB")
        prefs.addSafeLaunchPackages(listOf("com.bhxhapp", "com.VCB", "com.vnid", "  "))
        assertEquals(listOf("com.VCB", "com.bhxhapp", "com.vnid"), AppPreferences(context).safeLaunchPackages)

        val signals = DexAntiDebugSignals(
            checksAdbEnabled = true,
            checksDevSettings = true,
            checksAdbWifi = false,
            raspClassSdks = setOf("freeRASP", "BShield"),
        )
        prefs.putAllCachedDexSignals(
            mapOf(
                "com.example.hidden" to AppPreferences.CachedDexEntry(
                    lastUpdateTime = 1_700_000_000L,
                    scannerVersion = StrictAppDetector.SCANNER_VERSION,
                    signals = signals,
                )
            )
        )

        val reloaded = AppPreferences(context)
        val hit = reloaded.getCachedDexSignals("com.example.hidden", 1_700_000_000L)
        assertNotNull(hit)
        assertTrue(hit!!.checksAdbEnabled)
        assertTrue(hit.checksDevSettings)
        assertFalse(hit.checksAdbWifi)
        assertEquals(setOf("BShield", "freeRASP"), hit.raspClassSdks)

        // Update timestamp mismatch or scanner version mismatch invalidates cache entry.
        assertNull(reloaded.getCachedDexSignals("com.example.hidden", 1_700_000_001L))
        assertNull(
            reloaded.getCachedDexSignals(
                packageName = "com.example.hidden",
                lastUpdateTime = 1_700_000_000L,
                scannerVersion = StrictAppDetector.SCANNER_VERSION + 1,
            )
        )
    }
}
