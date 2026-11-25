package io.github.xom11.vibedebug.rules

import android.content.pm.ApplicationInfo
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile

/** Confidence level that an installed app blocks USB Debugging or Developer Options. */
enum class StrictAppConfidence {
    NONE,
    MEDIUM,
    HIGH,
}

/** Evidence source contributing to a [StrictAppAnalysis] score. */
enum class DetectionSource {
    CATALOG,
    RASP_SDK,
    SECURITY_SDK,
    DEX_ANTI_DEBUG,
    DOMAIN_HEURISTIC,
}

/** Signals extracted purely from APK ZIP entry names (no decompression). */
data class ApkStructureSignals(
    val raspSdks: Set<String> = emptySet(),
    val bankingSecuritySdks: Set<String> = emptySet(),
    val hasRootBeerNative: Boolean = false,
)

/** Signals extracted by streaming `classes*.dex` bytecode for anti-debug strings/SDK packages. */
data class DexAntiDebugSignals(
    val checksAdbEnabled: Boolean = false,
    val checksDevSettings: Boolean = false,
    val checksAdbWifi: Boolean = false,
    val raspClassSdks: Set<String> = emptySet(),
) {
    val hasAnySignal: Boolean
        get() = checksAdbEnabled || checksDevSettings || checksAdbWifi || raspClassSdks.isNotEmpty()
}

/** Combined multi-layer analysis explaining why an app is flagged for debug protection. */
data class StrictAppAnalysis(
    val confidence: StrictAppConfidence = StrictAppConfidence.NONE,
    val score: Int = 0,
    val sources: Set<DetectionSource> = emptySet(),
    val detectedSdks: List<String> = emptyList(),
    val checksAdbInDex: Boolean = false,
    val checksDevSettingsInDex: Boolean = false,
    val checksWirelessAdbInDex: Boolean = false,
    val category: StrictAppCategory? = null,
    val deepScanned: Boolean = false,
) {
    val isSuggested: Boolean get() = confidence != StrictAppConfidence.NONE
    val isHighConfidence: Boolean get() = confidence == StrictAppConfidence.HIGH
}

/**
 * Multi-pattern streaming ASCII matcher (Aho-Corasick) over raw byte streams.
 * Processes buffered chunks sequentially while preserving state across buffer boundaries.
 */
internal class AsciiPatternMatcher(patterns: List<String>) {

    private val patternCount = patterns.size
    private val flatNext: IntArray
    private val outputMasks: IntArray

    init {
        require(patterns.size <= Int.SIZE_BITS) { "At most ${Int.SIZE_BITS} patterns supported" }
        val trieNext = ArrayList<IntArray>()
        val trieOut = ArrayList<Int>()
        trieNext += IntArray(ALPHABET_SIZE) { -1 }
        trieOut += 0

        patterns.forEachIndexed { patternIdx, pattern ->
            var state = 0
            for (ch in pattern) {
                val code = ch.code
                require(code in 0 until ALPHABET_SIZE) { "Non-ASCII character in pattern: $pattern" }
                var nextState = trieNext[state][code]
                if (nextState == -1) {
                    nextState = trieNext.size
                    trieNext[state][code] = nextState
                    trieNext += IntArray(ALPHABET_SIZE) { -1 }
                    trieOut += 0
                }
                state = nextState
            }
            trieOut[state] = trieOut[state] or (1 shl patternIdx)
        }

        val fail = IntArray(trieNext.size)
        val queue = ArrayDeque<Int>()
        for (c in 0 until ALPHABET_SIZE) {
            val child = trieNext[0][c]
            if (child != -1) {
                fail[child] = 0
                queue.addLast(child)
            } else {
                trieNext[0][c] = 0
            }
        }

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val failU = fail[u]
            val rowU = trieNext[u]
            val rowFailU = trieNext[failU]
            for (c in 0 until ALPHABET_SIZE) {
                val v = rowU[c]
                if (v != -1) {
                    val fv = rowFailU[c]
                    fail[v] = fv
                    trieOut[v] = trieOut[v] or trieOut[fv]
                    queue.addLast(v)
                } else {
                    rowU[c] = rowFailU[c]
                }
            }
        }

        val stateCount = trieNext.size
        flatNext = IntArray(stateCount * ALPHABET_SIZE)
        outputMasks = IntArray(stateCount)
        for (s in 0 until stateCount) {
            trieNext[s].copyInto(flatNext, destinationOffset = s * ALPHABET_SIZE)
            outputMasks[s] = trieOut[s]
        }
    }

    internal data class StreamScanResult(
        val matched: Set<Int>,
        val bytesRead: Long,
    )

    fun scanStream(
        input: InputStream,
        maxBytes: Long,
        stopWhen: (Set<Int>) -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ): Set<Int> = scanStreamCounted(input, maxBytes, stopWhen, isCancelled).matched

    internal fun scanStreamCounted(
        input: InputStream,
        maxBytes: Long,
        stopWhen: (Set<Int>) -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ): StreamScanResult {
        if (maxBytes <= 0L) return StreamScanResult(emptySet(), 0L)
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var state = 0
        var foundMask = 0
        var totalRead = 0L
        val matched = HashSet<Int>()

        while (totalRead < maxBytes && !isCancelled()) {
            val remaining = (maxBytes - totalRead).coerceAtMost(buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, remaining)
            if (read <= 0) break
            totalRead += read

            for (i in 0 until read) {
                val b = buffer[i].toInt() and 0xFF
                state = if (b < ALPHABET_SIZE) flatNext[(state shl 7) + b] else 0
                val out = outputMasks[state]
                if (out != 0) {
                    var newlyFound = out and foundMask.inv()
                    if (newlyFound != 0) {
                        foundMask = foundMask or out
                        while (newlyFound != 0) {
                            val idx = Integer.numberOfTrailingZeros(newlyFound)
                            if (idx < patternCount) {
                                matched += idx
                            }
                            newlyFound = newlyFound and (newlyFound - 1)
                        }
                        if (stopWhen(matched)) {
                            return StreamScanResult(matched, totalRead)
                        }
                    }
                }
            }
        }
        return StreamScanResult(matched, totalRead)
    }

    companion object {
        private const val ALPHABET_SIZE = 128
        private const val READ_BUFFER_BYTES = 64 * 1024
    }
}

/**
 * 100% on-device detector identifying apps likely to block USB Debugging or Developer Options
 * using curated signatures, APK ZIP central-directory RASP scanning, domain heuristics,
 * and on-demand streaming DEX bytecode analysis.
 */
object StrictAppDetector {

    const val SCANNER_VERSION = 2
    const val MAX_DEX_BYTES_PER_APP = 32L * 1024L * 1024L

    private const val IDX_ADB_ENABLED = 0
    private const val IDX_DEV_SETTINGS = 1
    private const val IDX_ADB_WIFI = 2

    private val nonStructureSplitRegex = Regex(
        """^split_config\.([a-z]{2}(_[a-z0-9]+)?|[a-z]*dpi|tvdpi|nodpi)\.apk$""",
        RegexOption.IGNORE_CASE,
    )

    private val DEX_PATTERNS: List<String> = listOf(
        "adb_enabled",
        "development_settings_enabled",
        "adb_wifi_enabled",
        "com/aheaditec/talsec",
        "TalsecConfig",
        "com/verichains/bshield",
        "no/promon/shield",
        "com/vkey/android",
        "com/zimperium/zdefend",
        "com/licel/dexprotector",
        "com/inka/appsealing",
        "com/appdome",
    )

    private val DEX_RASP_SDK_BY_PATTERN_INDEX: Map<Int, String> = mapOf(
        3 to "freeRASP",
        4 to "freeRASP",
        5 to "BShield",
        6 to "Promon",
        7 to "V-Key",
        8 to "Zimperium",
        9 to "DexProtector",
        10 to "AppSealing",
        11 to "Appdome",
    )

    private val dexMatcher = AsciiPatternMatcher(DEX_PATTERNS)
    private val dexEntryRegex = Regex("""^classes(\d*)\.dex$""", RegexOption.IGNORE_CASE)
    private val labelWordRegex = Regex("""[\p{L}\p{N}]+""")

    private val PACKAGE_STRICT_TOKENS = setOf(
        "bank", "banking", "mbanking", "ebanking", "smartbanking", "digibank", "mobilebanking",
        "ewallet", "bhxh", "bhxhapp", "vssid", "vneid", "vnid", "etax", "dichvucong",
        "vnetraffic", "softotp", "smartotp", "singpass", "digilocker", "tcinvest",
        "smartone", "securities", "intune", "airwatch", "workspaceone", "mobileiron",
    )

    private val LABEL_STRICT_PHRASES = listOf(
        "ngân hàng", "dịch vụ công", "bảo hiểm xã hội", "thuế điện tử", "e-banking",
    )

    private val LABEL_STRICT_WORDS = setOf(
        "banking", "mbanking", "digibank", "vssid", "vneid", "etax", "bhxh", "singpass",
    )

    private val DAMPENED_CATEGORIES = setOf(
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE,
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_NEWS,
        ApplicationInfo.CATEGORY_MAPS,
    )

    /** Performs fast (<2ms/app) evaluation using catalog, APK structure, heuristics, and optional cached DEX signals. */
    fun analyzeFast(
        packageName: String,
        label: String,
        apkPaths: List<String>,
        appCategory: Int,
        cachedDex: DexAntiDebugSignals? = null,
    ): StrictAppAnalysis {
        val structure = inspectApkStructure(apkPaths)
        return evaluateSignals(
            packageName = packageName,
            label = label,
            structure = structure,
            dex = cachedDex,
            appCategory = appCategory,
            deepScanned = cachedDex != null,
        )
    }

    /** Runs on-demand streaming DEX scan for unflagged non-system apps; returns updated analysis and cacheable signals. */
    fun analyzeDeep(
        packageName: String,
        label: String,
        apkPaths: List<String>,
        appCategory: Int,
        fastAnalysis: StrictAppAnalysis,
        isSystemOnly: Boolean,
        isCancelled: () -> Boolean = { false },
    ): Pair<StrictAppAnalysis, DexAntiDebugSignals?> {
        if (isSystemOnly || fastAnalysis.isHighConfidence || fastAnalysis.deepScanned || apkPaths.isEmpty()) {
            return fastAnalysis to null
        }
        val structure = inspectApkStructure(apkPaths)
        val dex = scanApkDex(apkPaths, MAX_DEX_BYTES_PER_APP, isCancelled)
        if (isCancelled()) {
            return fastAnalysis to null
        }
        val updated = evaluateSignals(
            packageName = packageName,
            label = label,
            structure = structure,
            dex = dex,
            appCategory = appCategory,
            deepScanned = true,
        )
        return updated to dex
    }

    /** Returns false for locale/density split APKs that cannot contain native libraries or RASP assets. */
    internal fun isRelevantApkForStructure(path: String): Boolean {
        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        return !nonStructureSplitRegex.matches(fileName)
    }

    /** Inspects central directory entry names of [apkPaths] without decompressing entry payloads. */
    fun inspectApkStructure(apkPaths: List<String>): ApkStructureSignals {
        if (apkPaths.isEmpty()) return ApkStructureSignals()
        val entries = ArrayList<String>(64)
        for (apkPath in apkPaths) {
            if (!isRelevantApkForStructure(apkPath)) continue
            val file = File(apkPath)
            if (!file.isFile || !file.canRead()) continue
            runCatching {
                ZipFile(file).use { zip ->
                    val enumEntries = zip.entries()
                    while (enumEntries.hasMoreElements()) {
                        val name = enumEntries.nextElement().name
                        if (name.startsWith("lib/", ignoreCase = true) ||
                            name.startsWith("assets/", ignoreCase = true) ||
                            name.startsWith("META-INF/", ignoreCase = true)
                        ) {
                            entries += name
                        }
                    }
                }
            }
        }
        return analyzeApkEntryNames(entries.asSequence())
    }

    /** Streams `classes*.dex` entries across [apkPaths] through the Aho-Corasick matcher within [maxBytesPerApp]. */
    fun scanApkDex(
        apkPaths: List<String>,
        maxBytesPerApp: Long = MAX_DEX_BYTES_PER_APP,
        isCancelled: () -> Boolean = { false },
    ): DexAntiDebugSignals {
        if (apkPaths.isEmpty() || maxBytesPerApp <= 0L) return DexAntiDebugSignals()
        var remainingBudget = maxBytesPerApp
        val allMatched = HashSet<Int>()

        for (apkPath in apkPaths) {
            if (remainingBudget <= 0L || isCancelled()) break
            if (!isRelevantApkForStructure(apkPath)) continue
            val file = File(apkPath)
            if (!file.isFile || !file.canRead()) continue

            runCatching {
                ZipFile(file).use { zip ->
                    val dexEntries = ArrayList<Pair<Int, java.util.zip.ZipEntry>>()
                    val enumEntries = zip.entries()
                    while (enumEntries.hasMoreElements()) {
                        val entry = enumEntries.nextElement()
                        val match = dexEntryRegex.matchEntire(entry.name) ?: continue
                        val idx = match.groupValues[1].toIntOrNull() ?: 1
                        dexEntries += idx to entry
                    }
                    dexEntries.sortBy { it.first }

                    for ((_, entry) in dexEntries) {
                        if (remainingBudget <= 0L || isCancelled()) break
                        zip.getInputStream(entry).use { stream ->
                            val result = dexMatcher.scanStreamCounted(
                                input = stream,
                                maxBytes = remainingBudget,
                                stopWhen = { matched ->
                                    matched.any { it in DEX_RASP_SDK_BY_PATTERN_INDEX } ||
                                        (IDX_ADB_ENABLED in matched &&
                                            IDX_DEV_SETTINGS in matched &&
                                            IDX_ADB_WIFI in matched)
                                },
                                isCancelled = isCancelled,
                            )
                            remainingBudget = (remainingBudget - result.bytesRead).coerceAtLeast(0L)
                            allMatched += result.matched
                        }
                        if (hasTerminalDexMatch(allMatched)) break
                    }
                }
            }
            if (hasTerminalDexMatch(allMatched)) break
        }

        val raspSdks = allMatched.mapNotNullTo(LinkedHashSet()) { DEX_RASP_SDK_BY_PATTERN_INDEX[it] }
        return DexAntiDebugSignals(
            checksAdbEnabled = IDX_ADB_ENABLED in allMatched,
            checksDevSettings = IDX_DEV_SETTINGS in allMatched,
            checksAdbWifi = IDX_ADB_WIFI in allMatched,
            raspClassSdks = raspSdks,
        )
    }

    private fun hasTerminalDexMatch(matched: Set<Int>): Boolean =
        matched.any { it in DEX_RASP_SDK_BY_PATTERN_INDEX } ||
            (IDX_ADB_ENABLED in matched && IDX_DEV_SETTINGS in matched && IDX_ADB_WIFI in matched)

    internal fun analyzeApkEntryNames(entries: Sequence<String>): ApkStructureSignals {
        val raspSdks = LinkedHashSet<String>()
        val bankingSecuritySdks = LinkedHashSet<String>()
        var hasRootBeerNative = false

        val szipTokens = HashSet<String>()
        val odexTokens = HashSet<String>()
        val nativeLibTokens = HashSet<String>()

        for (raw in entries) {
            val path = raw.lowercase()
            val fileName = path.substringAfterLast('/')
            val isNativeLib = path.startsWith("lib/") && path.endsWith(".so")

            if (path.startsWith("assets/")) {
                val rel = path.removePrefix("assets/")
                if (rel.endsWith(".szip") && '/' !in rel) {
                    szipTokens += rel.removeSuffix(".szip")
                } else if (rel.endsWith("/0.odex")) {
                    val token = rel.removeSuffix("/0.odex")
                    if (token.isNotEmpty() && '/' !in token) {
                        odexTokens += token
                    }
                }
            }
            if (isNativeLib && fileName.startsWith("lib")) {
                nativeLibTokens += fileName.removePrefix("lib").removeSuffix(".so")
            }

            // RASP / Anti-Debug Shield signatures
            val libBaseName = if (isNativeLib) fileName.removePrefix("lib") else fileName
            if (path == "assets/bshield.dat" ||
                (path.startsWith("assets/") && "bshield" in fileName) ||
                (isNativeLib && "bshield" in libBaseName)
            ) {
                raspSdks += "BShield"
            }
            if (isNativeLib && (fileName.startsWith("libdexprotector") || fileName.startsWith("libdpboot"))) {
                raspSdks += "DexProtector"
            }
            if (path == "assets/vkeylicensepack" ||
                path.startsWith("assets/v-key") ||
                path.startsWith("assets/vguard") ||
                (isNativeLib && (
                    fileName.startsWith("libvoswrapper") ||
                        fileName.startsWith("libvguard") ||
                        fileName == "libvtap.so" ||
                        fileName == "libloadta.so"
                    ))
            ) {
                raspSdks += "V-Key"
            }
            if ((isNativeLib && (fileName.startsWith("libpromon") || fileName == "libshield.so")) ||
                path.startsWith("assets/promon")
            ) {
                raspSdks += "Promon"
            }
            if ((isNativeLib && fileName.startsWith("libappdome")) || path.startsWith("assets/appdome")) {
                raspSdks += "Appdome"
            }
            if ((isNativeLib && fileName.startsWith("libappsealing")) || path.startsWith("assets/appsealing")) {
                raspSdks += "AppSealing"
            }
            if (isNativeLib && (fileName.startsWith("libzdefend") || fileName.startsWith("libzshield"))) {
                raspSdks += "Zimperium"
            }
            if ((isNativeLib && (fileName.startsWith("libtalsec") || fileName.startsWith("libfreerasp"))) ||
                (path.startsWith("meta-inf/") && ("talsec" in path || "freerasp" in path))
            ) {
                raspSdks += "freeRASP"
            }
            if (isNativeLib && fileName.startsWith("libverimatrix")) {
                raspSdks += "Verimatrix"
            }
            if ((isNativeLib && fileName.startsWith("libliapp")) || path.startsWith("assets/liapp")) {
                raspSdks += "LIAPP"
            }
            if ((isNativeLib && fileName.startsWith("libappguard")) || path.startsWith("assets/appguard")) {
                raspSdks += "AppGuard"
            }
            if (isNativeLib && fileName.startsWith("libdxshield")) {
                raspSdks += "DxShield"
            }
            if (isNativeLib && (fileName.startsWith("libbangcle") || fileName.startsWith("libsecneo"))) {
                raspSdks += "Bangcle"
            }
            if (isNativeLib && fileName.startsWith("libjiagu")) {
                raspSdks += "Qihoo Jiagu"
            }
            if (isNativeLib && (fileName.startsWith("libnprotect") || fileName.startsWith("libv3mobile"))) {
                raspSdks += "AhnLab / nProtect"
            }
            if (isNativeLib && (fileName.startsWith("libxigncode") || fileName.startsWith("libace_hts"))) {
                raspSdks += "Anti-Cheat"
            }

            // Banking / Fraud / SoftOTP Security SDK signatures
            if (isNativeLib && fileName.startsWith("libtmxprofiling")) {
                bankingSecuritySdks += "ThreatMetrix"
            }
            if (isNativeLib && (
                    fileName.startsWith("libsoftotp") ||
                        fileName == "libvds-sotp.so" ||
                        fileName.startsWith("libkeypasssofttoken") ||
                        fileName.startsWith("libsmartotp")
                    )
            ) {
                bankingSecuritySdks += "SoftOTP"
            }
            if (isNativeLib && (
                    fileName == "libnative_security.so" ||
                        fileName == "libsecuritylib.so" ||
                        fileName == "libsecurity_lite.so" ||
                        fileName == "libcom.bina.security.secsdk.so" ||
                        fileName == "libvtbpcidss.so" ||
                        fileName.startsWith("libarxan")
                    )
            ) {
                bankingSecuritySdks += "Security SDK"
            }

            // RootBeer native binary (explicitly excludes librootchecker.so used by media apps)
            if (isNativeLib && fileName == "libtoolchecker.so") {
                hasRootBeerNative = true
            }
        }

        if (szipTokens.any { token -> token in odexTokens || token in nativeLibTokens }) {
            raspSdks += "Shield"
        }

        return ApkStructureSignals(
            raspSdks = raspSdks,
            bankingSecuritySdks = bankingSecuritySdks,
            hasRootBeerNative = hasRootBeerNative,
        )
    }

    internal fun matchesStrictDomainHeuristic(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg.startsWith("com.vnpay.") ||
            pkg.startsWith("vn.gov.") ||
            pkg.startsWith("sg.gov.") ||
            ".gov." in pkg
        ) {
            return true
        }
        val pkgSegments = pkg.split('.', '_')
        if (pkgSegments.any { it in PACKAGE_STRICT_TOKENS }) {
            return true
        }

        val labelLower = label.lowercase()
        if (LABEL_STRICT_PHRASES.any { it in labelLower }) {
            return true
        }
        return labelWordRegex.findAll(labelLower).any { it.value in LABEL_STRICT_WORDS }
    }

    internal fun evaluateSignals(
        packageName: String,
        label: String,
        structure: ApkStructureSignals,
        dex: DexAntiDebugSignals?,
        appCategory: Int,
        deepScanned: Boolean = dex != null,
    ): StrictAppAnalysis {
        var score = 0
        val sources = LinkedHashSet<DetectionSource>()

        val catalogCategory = StrictAppCatalog.categoryFor(packageName)
        if (catalogCategory != null) {
            score += 100
            sources += DetectionSource.CATALOG
        }

        val allRaspSdks = (structure.raspSdks + dex?.raspClassSdks.orEmpty()).sorted()
        if (allRaspSdks.isNotEmpty()) {
            score += 95
            sources += DetectionSource.RASP_SDK
        }

        if (structure.bankingSecuritySdks.isNotEmpty()) {
            score += if ("ThreatMetrix" in structure.bankingSecuritySdks ||
                "SoftOTP" in structure.bankingSecuritySdks
            ) {
                75
            } else {
                55
            }
            sources += DetectionSource.SECURITY_SDK
        }

        val domainMatch = matchesStrictDomainHeuristic(packageName, label)
        if (domainMatch) {
            score += 30
            sources += DetectionSource.DOMAIN_HEURISTIC
        }

        if (structure.hasRootBeerNative) {
            score += 20
        }

        val isDampenedCategory = appCategory in DAMPENED_CATEGORIES
        if (dex != null) {
            val rawDexScore = when {
                dex.checksAdbWifi -> 60
                dex.checksAdbEnabled && dex.checksDevSettings -> 55
                dex.checksAdbEnabled || dex.checksDevSettings -> 35
                else -> 0
            }
            val effectiveDexScore = if (
                rawDexScore > 0 &&
                isDampenedCategory &&
                !domainMatch &&
                allRaspSdks.isEmpty() &&
                structure.bankingSecuritySdks.isEmpty()
            ) {
                (rawDexScore - 30).coerceAtLeast(0)
            } else {
                rawDexScore
            }
            if (effectiveDexScore > 0) {
                score += effectiveDexScore
                sources += DetectionSource.DEX_ANTI_DEBUG
            }
        }

        val strongSecuritySdks = structure.bankingSecuritySdks
            .filter { it == "ThreatMetrix" || it == "SoftOTP" }
            .sorted()
        val otherSecuritySdks = structure.bankingSecuritySdks
            .filterNot { it == "ThreatMetrix" || it == "SoftOTP" }
            .sorted()
        val detectedSdks = (allRaspSdks + strongSecuritySdks + otherSecuritySdks).distinct()
        val confidence = when {
            score >= 75 -> StrictAppConfidence.HIGH
            score >= 45 -> StrictAppConfidence.MEDIUM
            else -> StrictAppConfidence.NONE
        }

        return StrictAppAnalysis(
            confidence = confidence,
            score = score,
            sources = sources,
            detectedSdks = detectedSdks,
            checksAdbInDex = dex?.checksAdbEnabled == true,
            checksDevSettingsInDex = dex?.checksDevSettings == true,
            checksWirelessAdbInDex = dex?.checksAdbWifi == true,
            category = catalogCategory,
            deepScanned = deepScanned,
        )
    }
}
