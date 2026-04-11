import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.floor

val generatedThemeAssetsDir = layout.buildDirectory.dir("generated/theme-assets").get().asFile
val generatedOsmPoiIconsAssetsDir = layout.buildDirectory.dir("generated/osm-poi-icons").get().asFile
val generatedLicenseAssetsDir = layout.buildDirectory.dir("generated/license-assets").get().asFile
val elevateThemeVersion = providers.gradleProperty("elevateThemeVersion").orElse("5.6")
val elevateThemeUrl = providers.gradleProperty("elevateThemeUrl")
    .orElse("https://www.openandromaps.org/wp-content/users/tobias/Elevate.zip")
val elevateThemeSha256 = providers.gradleProperty("elevateThemeSha256").orElse("")
val elevateThemeDownloadEnabled = providers.gradleProperty("elevateThemeDownloadEnabled").orElse("true")
val elevateThemeAllowFallback = providers.gradleProperty("elevateThemeAllowFallback").orElse("true")
val elevateThemeForceRefresh = providers.gradleProperty("elevateThemeForceRefresh").orElse("false")
val elevateWinterThemeVersion = providers.gradleProperty("elevateWinterThemeVersion").orElse("5.6")
val elevateWinterThemeUrl = providers.gradleProperty("elevateWinterThemeUrl")
    .orElse("https://www.senotto.de/Tipps_Tricks/GPS/OAM_Winter/Elevate_Winter.zip")
val elevateWinterThemeSha256 = providers.gradleProperty("elevateWinterThemeSha256").orElse("")
val elevateWinterThemeDownloadEnabled = providers.gradleProperty("elevateWinterThemeDownloadEnabled").orElse("true")
val elevateWinterThemeAllowFallback = providers.gradleProperty("elevateWinterThemeAllowFallback").orElse("true")
val elevateWinterThemeForceRefresh = providers.gradleProperty("elevateWinterThemeForceRefresh").orElse("false")
val openHikingThemeVersion = providers.gradleProperty("openHikingThemeVersion").orElse("2026-02-10")
val openHikingThemeUrl = providers.gradleProperty("openHikingThemeUrl")
    .orElse("https://openhiking.eu/en/component/phocadownload/category/1-terkepek/6-mapsforge-stilus?Itemid=102&download=14:openhiking-terkep-stilus")
val openHikingThemeSha256 = providers.gradleProperty("openHikingThemeSha256").orElse("")
val openHikingThemeDownloadEnabled = providers.gradleProperty("openHikingThemeDownloadEnabled").orElse("true")
val openHikingThemeAllowFallback = providers.gradleProperty("openHikingThemeAllowFallback").orElse("true")
val openHikingThemeForceRefresh = providers.gradleProperty("openHikingThemeForceRefresh").orElse("false")
val embeddedOpenHikingThemeDir = project.file("src/main/assets/theme/openhiking")
val embeddedOpenHikingThemeXml = File(embeddedOpenHikingThemeDir, "OpenHiking.xml")

val dem3BaseUrl = providers.gradleProperty("dem3BaseUrl")
    .orElse("https://download.mapsforge.org/maps/dem/dem3")
val dem3Tiles = providers.gradleProperty("dem3Tiles").orElse("")
val dem3Bbox = providers.gradleProperty("dem3Bbox").orElse("")
val dem3Overwrite = providers.gradleProperty("dem3Overwrite").orElse("false")
val dem3FailOnMissing = providers.gradleProperty("dem3FailOnMissing").orElse("true")
val dem3OutputDirPath = providers.gradleProperty("dem3OutputDir")
    .orElse(layout.buildDirectory.dir("generated/dem3").get().asFile.absolutePath)

val osmPoiIconsDownloadEnabled = providers.gradleProperty("osmPoiIconsDownloadEnabled").orElse("true")
val osmPoiIconsAllowFallback = providers.gradleProperty("osmPoiIconsAllowFallback").orElse("true")
val osmPoiIconsOverwrite = providers.gradleProperty("osmPoiIconsOverwrite").orElse("true")
val osmPoiIconsFailOnMissing = providers.gradleProperty("osmPoiIconsFailOnMissing").orElse("false")
val makiIconsBaseUrl = providers.gradleProperty("makiIconsBaseUrl")
    .orElse("https://raw.githubusercontent.com/mapbox/maki/main/icons")

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun extractThemeFromZip(
    zipFile: File,
    outputRoot: File,
    targetThemeDir: String,
    xmlCandidates: List<String>,
    outputXmlFileName: String
) {
    val themeRoot = File(outputRoot, targetThemeDir)
    val normalizedXmlCandidates = xmlCandidates
        .map { it.trim().lowercase(Locale.ROOT).removePrefix("/") }
        .filter { it.isNotEmpty() }
        .toSet()
    var matchedXmlEntryPath: String? = null
    var matchedSourceRoot = ""

    ZipInputStream(Files.newInputStream(zipFile.toPath()).buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                val rawPath = entry.name.replace('\\', '/').trimStart('/')
                val lowerPath = rawPath.lowercase(Locale.ROOT)
                if (normalizedXmlCandidates.any { candidate ->
                        lowerPath == candidate || lowerPath.endsWith("/$candidate")
                    }
                ) {
                    matchedXmlEntryPath = rawPath
                    matchedSourceRoot = rawPath.substringBeforeLast('/', "")
                    zip.closeEntry()
                    break
                }
            }
            zip.closeEntry()
        }
    }

    val xmlEntryPath = matchedXmlEntryPath ?: throw GradleException(
        "Theme ZIP did not contain expected XML (${xmlCandidates.joinToString()}): ${zipFile.absolutePath}"
    )
    val xmlOut = File(themeRoot, outputXmlFileName)
    var copiedEntries = 0

    ZipInputStream(Files.newInputStream(zipFile.toPath()).buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue

            val rawPath = entry.name.replace('\\', '/').trimStart('/')
            val relativePath = when {
                matchedSourceRoot.isEmpty() -> rawPath
                rawPath == matchedSourceRoot -> ""
                rawPath.startsWith("$matchedSourceRoot/") -> rawPath.removePrefix("$matchedSourceRoot/")
                else -> null
            }
            if (relativePath.isNullOrBlank()) {
                zip.closeEntry()
                continue
            }

            val outputRelativePath = if (rawPath == xmlEntryPath) outputXmlFileName else relativePath
            val target = File(themeRoot, outputRelativePath)
            target.parentFile?.mkdirs()
            Files.newOutputStream(target.toPath()).use { out ->
                zip.copyTo(out)
            }
            copiedEntries += 1
            zip.closeEntry()
        }
    }

    if (!xmlOut.exists() || copiedEntries == 0) {
        throw GradleException(
            "Theme ZIP did not produce usable extracted assets: ${zipFile.absolutePath}"
        )
    }
}

fun createWhiteSkiThemeVariant(
    outputRoot: File,
    sourceThemeDir: String,
    targetThemeDir: String
): Boolean {
    val sourceRoot = File(outputRoot, sourceThemeDir)
    val sourceXml = File(sourceRoot, "Elevate.xml")
    if (!sourceXml.exists()) return false

    val targetRoot = File(outputRoot, targetThemeDir)
    if (targetRoot.exists()) targetRoot.deleteRecursively()
    sourceRoot.copyRecursively(targetRoot, overwrite = true)

    val targetXml = File(targetRoot, "Elevate.xml")
    if (!targetXml.exists()) return false

    val colorMap = mapOf(
        "F8F8F8" to "FFFFFF",
        "EEEEEE" to "FFFFFF",
        "DDDDDD" to "FFFFFF",
        "E6DFDF" to "F3F3F3",
        "D9D0C7" to "EFEFEF",
        "B3ABA4" to "E5E5E5",
        "B3A18F" to "DEDEDE",
        "A69D9D" to "DFDFDF",
        "8E8072" to "D7D7D7",
        "997755" to "D3D3D3",
        "925D3A" to "CDCDCD",
        "734A08" to "C2C2C2",
        "779977" to "E1EAE1",
        "6F996F" to "DCE7DC",
        "799079" to "DFE7DF",
        "B3DDFF" to "D7EEFF",
        "BBF0F2E9" to "EAF4EA",
        "BBFFE6FC" to "F2EBF2",
        "BBE9F4F7" to "EEF2F4",
        "BBC7F1A3" to "E8F2E0",
        "F7F0D4" to "F4F0E6",
        // Stronger contour softening for cleaner ski-white readability.
        "888888" to "CFCFCF",
        "AAAAAA" to "DADADA",
        "666666" to "C2C2C2",
        "333333" to "B2B2B2",
        // Desaturate non-ski base roads/features.
        "C9820D" to "B5B5B5",
        "9C6800" to "A9A9A9",
        "FFBB00" to "C8C8C8",
        "B3A18F" to "DCDCDC",
        // Muted cool accents used by winter-road / mixed piste-road rendering.
        "B36C4C" to "A5B6C7",
        "F2A07B" to "CDD9E6",
        "C4B29F" to "D5DEE8",
        "99855C" to "B2C0CD",
        // Ski-area fills in mixed road/piste segments: keep subtle and cool.
        "DDF2A07B" to "33C3D7EA"
    )

    val overlaysToDisableByDefault = listOf(
        "elv-h_routes",
        "elv-h_s_routes",
        "elv-c_routes",
        "elv-c_s_routes",
        "elv-waymarks",
        "elv-pt_network",
        "elv-amenities",
        "elv-sports",
        "elv-emergency",
        "elv-restaurants",
        "elv-shops",
        "elv-tourism",
        "elv-pubtrans",
        "elv-car-h",
        "elv-buildings",
        "elv-winter_symbol",
        "elv-winter_reference"
    )

    fun remapPatternHex(rawHex: String, explicitMap: Map<String, String>): String? {
        val upper = rawHex.uppercase(Locale.ROOT)
        explicitMap[upper]?.let { return it }
        if (upper.length == 8) {
            val alpha = upper.substring(0, 2)
            explicitMap[upper.substring(2)]?.let { mapped -> return alpha + mapped }
        }

        val rgb = when (upper.length) {
            6 -> upper
            8 -> upper.substring(2)
            else -> return null
        }
        val r = rgb.substring(0, 2).toInt(16)
        val g = rgb.substring(2, 4).toInt(16)
        val b = rgb.substring(4, 6).toInt(16)

        // Fallback for any remaining green-dominant pattern tone.
        if (g > r + 6 && g > b + 3) {
            val lum = (0.30 * r + 0.59 * g + 0.11 * b)
            val nr = (lum * 0.89 + 8.0).toInt().coerceIn(0, 255)
            val ng = (lum * 0.96 + 12.0).toInt().coerceIn(0, 255)
            val nb = (lum * 1.08 + 18.0).toInt().coerceIn(0, 255)
            val mappedRgb = String.format(Locale.US, "%02X%02X%02X", nr, ng, nb)
            return if (upper.length == 8) upper.substring(0, 2) + mappedRgb else mappedRgb
        }
        return null
    }

    var transformed = targetXml.readText()
    transformed = transformed.replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
        val raw = match.groupValues[1]
        val mapped = colorMap[raw.uppercase(Locale.ROOT)] ?: return@replace match.value
        "#$mapped"
    }
    // Global whitening lift for the derived palette (applied after base remap).
    val whiteLiftMap = mapOf(
        "CFCFCF" to "E3E3E3",
        "C2C2C2" to "DDDDDD",
        "B2B2B2" to "D4D4D4",
        "DCDCDC" to "EBEBEB",
        "DADADA" to "EAEAEA",
        "E5E5E5" to "F1F1F1",
        "EFEFEF" to "F6F6F6",
        "B5B5B5" to "D8D8D8",
        "DFDFDF" to "EDEDED",
        "A9A9A9" to "D1D1D1",
        "D7D7D7" to "E8E8E8",
        "D3D3D3" to "E3E3E3",
        // Forest/nature tint shift from green to cool light blue-gray.
        "EAF4EA" to "EEF3F8",
        "E8F2E0" to "EAF0F7",
        "D3EBD9" to "E5EDF5",
        "A5CBA5" to "D5E2EE",
        "6FC18E" to "B8CAD8",
        "6FC13D" to "B5C6D6",
        "E1EAE1" to "E6EDF4",
        "DCE7DC" to "E3ECF4",
        "DFE7DF" to "E5EDF5",
        // Soften dark outlines/alpha overlays for a lighter ski canvas.
        "222222" to "676767",
        "404040" to "757575",
        "AA000000" to "66000000",
        "30000000" to "18000000"
    )
    transformed = transformed.replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
        val raw = match.groupValues[1]
        val mapped = whiteLiftMap[raw.uppercase(Locale.ROOT)] ?: return@replace match.value
        "#$mapped"
    }
    val xmlGreenRemap = mapOf(
        // Remaining vegetation/agriculture tints in winter XML.
        "DDD3EBD9" to "DDECF3F8",
        "BBE6FFDB" to "BBEDF3F8",
        "BBF5FFDB" to "BBF0F4F8",
        "BBD3EBD9" to "BBE8EFF6",
        "66D3EBD9" to "66E8EFF6",
        "CBE1CB" to "E3EAF1",
        "909DFF9C" to "90BED2E6",
        "A688BD8C" to "A6B4C8DB",
        "5989BE8C" to "59AFC4D9",
        "40DDFFDD" to "40DCEAF5",
        // Remaining warm specialty fills: keep the white ski style in one cool family.
        "BBFCF7E3" to "BBEEF3F8",
        "BBF2EFE4" to "BBEEF3F8",
        "CCF2EFE4" to "CCE7EEF5",
        "BBFAEBB9" to "BBEEF3F8",
        "FFFFD1" to "F2F5F8",
        "E9DD72" to "B5C5D3",
        "9EA199" to "B7C5D3",
        // Legacy green caption/line colors.
        "39AC39" to "4D7CA2",
        "38732E" to "5C7994",
        "267F00" to "5C7E9D",
        "00FF00" to "73ABD8",
        "00CC00" to "5F93C3",
        "009900" to "547EA0",
        "008800" to "4D7495",
        "44DD44" to "7DAED4",
        "00DD00" to "679AC8"
    )
    transformed = transformed.replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
        val raw = match.groupValues[1]
        val mapped = remapPatternHex(raw, xmlGreenRemap) ?: return@replace match.value
        "#$mapped"
    }
    // White variant: neutralize remaining warm/orange road palette to grayscale.
    val warmRoadRemap = mapOf(
        "FFEC8B" to "D6D6D6",
        "FFDF33" to "CECECE",
        // Keep snow-park / halfpipe dash palette cool in White ski style.
        "FFFF00" to "BFD5EB",
        "FFBB00" to "A7C2DE",
        "FFFF88" to "DADADA",
        "F7DE4B" to "CCCCCC",
        "FF4D00" to "A7A7A7",
        "F2CC00" to "C9C9C9",
        "FF9500" to "B9B9B9",
        "FF7700" to "AFAFAF",
        "FFAA00" to "BDBDBD",
        "EED800" to "CDCDCD",
        "EB8D00" to "B3B3B3",
        "BC6823" to "9F9F9F",
        "AB7846" to "A5A5A5",
        "CC7466" to "B0B0B0",
        "DE781F" to "B5B5B5",
        "E69900" to "BCBCBC",
        "997B29" to "9A9A9A",
        "FFD900" to "D0D0D0"
    )
    transformed = transformed.replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
        val raw = match.groupValues[1]
        val mapped = remapPatternHex(raw, warmRoadRemap) ?: return@replace match.value
        "#$mapped"
    }
    // White variant: slightly strengthen water readability and keep labels in the same cool family.
    val waterLegendRemap = mapOf(
        "D7EEFF" to "C5E3FF",
        "B3DDFF" to "C5E3FF",
        "DDB3DDFF" to "D9EBFF",
        "77A5FC" to "6C98DA"
    )
    transformed = transformed.replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
        val raw = match.groupValues[1]
        val mapped = remapPatternHex(raw, waterLegendRemap) ?: return@replace match.value
        "#$mapped"
    }
    // Reduce only ski-piste area fill opacity; keep line strokes strong.
    transformed = transformed.replace(Regex("""(<area\s+fill="#)33([0-9A-Fa-f]{6})(")""")) {
        "${it.groupValues[1]}15${it.groupValues[2]}${it.groupValues[3]}"
    }
    // White variant: avoid dense wood texture symbols at close zoom (they can read green on small OLEDs).
    transformed = transformed.replace(
        Regex("""<area\s+src="file:ele-res/p_wood-(?:coniferous|deciduous|mixed)\.svg"\s+symbol-height="[^"]+"\s*/>"""),
        "<area fill=\"#C9D8E6\" />"
    )
    // White variant: force orchard/vineyard/nursery to one custom blue dotted texture.
    transformed = transformed.replace(
        Regex("""file:ele-res/p_(?:orchard|vineyard|plant_nursery)\.svg"""),
        "file:ele-res/p_dotted_blue.svg"
    )
    overlaysToDisableByDefault.forEach { overlayId ->
        transformed = transformed.replace(
            Regex("(<layer\\s+id=\"$overlayId\"\\s+enabled=\")true(\")"),
            "$1false$2"
        )
    }
    transformed = transformed.replace(
        "<name lang=\"en\" value=\"Wintersport\" />",
        "<name lang=\"en\" value=\"White ski\" />"
    )
    transformed = transformed.replace(
        "<name lang=\"fr\" value=\"Sports d'hiver\" />",
        "<name lang=\"fr\" value=\"Ski blanc\" />"
    )
    transformed = transformed.replace(
        Regex("""(<stylemenu[^>]*\bdefaultvalue=")[^"]+(")"""),
        "$1elv-winter$2"
    )
    targetXml.writeText(transformed)

    val eleResDir = File(targetRoot, "ele-res")
    if (eleResDir.exists()) {
        val dottedBluePattern = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="580" height="580" version="1.0">
             <rect width="580" height="580" fill="#EAF3FB" opacity="0.8"/>
             <path d="M 161.77833,115.8587 A 45.213154,45.213154 0 0 1 116.56518,161.07186 45.213154,45.213154 0 0 1 71.352024,115.8587 45.213154,45.213154 0 0 1 116.56518,70.64555 45.213154,45.213154 0 0 1 161.77833,115.8587 Z" fill="#9EB5CB" opacity="0.28"/>
             <path d="m 143.41048,334.15347 a 45.213154,45.213154 0 0 1 -45.213154,45.21316 45.213154,45.213154 0 0 1 -45.21316,-45.21316 45.213154,45.213154 0 0 1 45.21316,-45.21315 45.213154,45.213154 0 0 1 45.213154,45.21315 z" fill="#9EB5CB" opacity="0.28"/>
             <path d="m 433.05724,170.25578 a 45.213154,45.213154 0 0 1 -45.21315,45.21316 45.213154,45.213154 0 0 1 -45.21315,-45.21316 45.213154,45.213154 0 0 1 45.21315,-45.21315 45.213154,45.213154 0 0 1 45.21315,45.21315 z" fill="#9EB5CB" opacity="0.28"/>
             <path d="m 291.05969,463.43483 a 45.213154,45.213154 0 0 1 -45.21315,45.21316 45.213154,45.213154 0 0 1 -45.21316,-45.21316 45.213154,45.213154 0 0 1 45.21316,-45.21315 45.213154,45.213154 0 0 1 45.21315,45.21315 z" fill="#9EB5CB" opacity="0.28"/>
             <path d="m 524.89647,351.1084 a 45.213154,45.213154 0 0 1 -45.21315,45.21316 45.213154,45.213154 0 0 1 -45.21316,-45.21316 45.213154,45.213154 0 0 1 45.21316,-45.21315 45.213154,45.213154 0 0 1 45.21315,45.21315 z" fill="#9EB5CB" opacity="0.28"/>
            </svg>
        """.trimIndent()
        File(eleResDir, "p_dotted_blue.svg").writeText(dottedBluePattern)

        val patternColorMap = mapOf(
            "99BD77" to "CADAE9",
            "88BD8C" to "C7D8E8",
            "78AB90" to "BDD2E5",
            "89BE8C" to "C8D9E9",
            "AFCFA9" to "DAE7F3",
            "A3CCA6" to "D7E5F2",
            "BCCFB5" to "DDE8F2",
            "DAE9A1" to "E7EEF6",
            "EBF7E4" to "F1F6FB",
            "F0F6ED" to "F3F7FB",
            "F1F7F0" to "F4F7FC",
            "D2EAD8" to "E4EDF6",
            "9DFF9B" to "CEE2F0",
            "779977" to "D3E0EB",
            "6F996F" to "D0DEEA",
            "799079" to "D5E1EC",
            // Keep dotted textures but shift them to blue tones.
            "E0F5FF" to "EAF3FB",
            "FF6B23" to "B8CAD9",
            "A401FF" to "B8CAD9",
            "D7E5F2" to "D0DCE8",
            // Soften the blue/gray texture contrast for a calmer white base.
            "DAE7F3" to "E7EFF6",
            "E7EEF6" to "EEF3F8",
            "C4D6F6" to "D9E6F2",
            "B3DDFF" to "D6EAFF",
            "C3FAFA" to "DDEFFF",
            "E0FFFF" to "EEF7FF"
        )
        val blueDottedLanduseFiles = setOf("p_orchard.svg", "p_vineyard.svg", "p_plant_nursery.svg")
        eleResDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("svg", ignoreCase = true) && it.name.startsWith("p_") }
            .forEach { patternSvg ->
                var updated = patternSvg.readText().replace(Regex("#([0-9A-Fa-f]{6,8})")) { match ->
                    val raw = match.groupValues[1]
                    val mapped = remapPatternHex(raw, patternColorMap) ?: return@replace match.value
                    "#$mapped"
                }
                if (patternSvg.name in blueDottedLanduseFiles) {
                    // Force a stable blue palette for dotted landuse polygons in White ski style.
                    updated = updated
                        .replace("#E0F5FF", "#EAF3FB")
                        .replace("#D7E5F2", "#B8CBDD")
                        .replace("#FF6B23", "#9EB5CB")
                        .replace("#A401FF", "#9EB5CB")
                }
                patternSvg.writeText(updated)
            }
    }

    return true
}

data class DemTile(val lat: Int, val lon: Int) {
    fun id(): String {
        val latPrefix = if (lat >= 0) "N" else "S"
        val lonPrefix = if (lon >= 0) "E" else "W"
        val latAbs = abs(lat)
        val lonAbs = abs(lon)
        return String.format(Locale.US, "%s%02d%s%03d", latPrefix, latAbs, lonPrefix, lonAbs)
    }
}

fun parseDemTileId(raw: String): DemTile? {
    val token = raw.trim().uppercase(Locale.ROOT)
    val regex = Regex("^([NS])(\\d{2})([EW])(\\d{3})$")
    val match = regex.matchEntire(token) ?: return null
    val latValue = match.groupValues[2].toInt()
    val lonValue = match.groupValues[4].toInt()
    val lat = if (match.groupValues[1] == "N") latValue else -latValue
    val lon = if (match.groupValues[3] == "E") lonValue else -lonValue
    return DemTile(lat = lat, lon = lon)
}

fun tilesFromBboxOrThrow(rawBbox: String): Set<String> {
    val token = rawBbox.trim()
    if (token.isEmpty()) return emptySet()

    val parts = token.split(',').map { it.trim() }
    if (parts.size != 4) {
        throw GradleException("dem3Bbox must be 'minLat,minLon,maxLat,maxLon'")
    }

    val minLat = parts[0].toDoubleOrNull() ?: throw GradleException("dem3Bbox: invalid minLat")
    val minLon = parts[1].toDoubleOrNull() ?: throw GradleException("dem3Bbox: invalid minLon")
    val maxLat = parts[2].toDoubleOrNull() ?: throw GradleException("dem3Bbox: invalid maxLat")
    val maxLon = parts[3].toDoubleOrNull() ?: throw GradleException("dem3Bbox: invalid maxLon")

    if (minLat >= maxLat || minLon >= maxLon) {
        throw GradleException("dem3Bbox invalid: min must be lower than max")
    }

    val endLatInclusive = floor(Math.nextDown(maxLat)).toInt()
    val endLonInclusive = floor(Math.nextDown(maxLon)).toInt()
    val startLat = floor(minLat).toInt()
    val startLon = floor(minLon).toInt()

    val result = linkedSetOf<String>()
    for (lat in startLat..endLatInclusive) {
        for (lon in startLon..endLonInclusive) {
            result += DemTile(lat, lon).id()
        }
    }
    return result
}

fun tilePathSegments(tileId: String): Pair<String, String> {
    val upper = tileId.uppercase(Locale.ROOT)
    val folder = upper.substring(0, 3)
    val file = "$upper.hgt.zip"
    return folder to file
}

data class PoiIconSpec(
    val assetName: String,
    val remoteCandidates: List<String>
)

tasks.register("prepareBundledThemeAssets") {
    group = "build setup"
    description = "Downloads and extracts bundled Mapsforge themes into generated assets."
    outputs.dir(generatedThemeAssetsDir)
    inputs.properties(
        mapOf(
            "elevateThemeVersion" to elevateThemeVersion.get(),
            "elevateThemeUrl" to elevateThemeUrl.get(),
            "elevateThemeSha256" to elevateThemeSha256.get(),
            "elevateThemeDownloadEnabled" to elevateThemeDownloadEnabled.get(),
            "elevateThemeAllowFallback" to elevateThemeAllowFallback.get(),
            "elevateThemeForceRefresh" to elevateThemeForceRefresh.get(),
            "elevateWinterThemeVersion" to elevateWinterThemeVersion.get(),
            "elevateWinterThemeUrl" to elevateWinterThemeUrl.get(),
            "elevateWinterThemeSha256" to elevateWinterThemeSha256.get(),
            "elevateWinterThemeDownloadEnabled" to elevateWinterThemeDownloadEnabled.get(),
            "elevateWinterThemeAllowFallback" to elevateWinterThemeAllowFallback.get(),
            "elevateWinterThemeForceRefresh" to elevateWinterThemeForceRefresh.get(),
            "openHikingThemeVersion" to openHikingThemeVersion.get(),
            "openHikingThemeUrl" to openHikingThemeUrl.get(),
            "openHikingThemeSha256" to openHikingThemeSha256.get(),
            "openHikingThemeDownloadEnabled" to openHikingThemeDownloadEnabled.get(),
            "openHikingThemeAllowFallback" to openHikingThemeAllowFallback.get(),
            "openHikingThemeForceRefresh" to openHikingThemeForceRefresh.get(),
            "embeddedOpenHikingThemeXmlPresent" to embeddedOpenHikingThemeXml.exists()
        )
    )

    doLast {
        val outputRoot = generatedThemeAssetsDir
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val downloadDir = layout.buildDirectory.dir("generated/theme-download").get().asFile
        downloadDir.mkdirs()

        fun prepareTheme(
            themeLabel: String,
            version: String,
            url: String,
            expectedSha: String,
            forceRefresh: Boolean,
            allowFallback: Boolean,
            zipFileName: String,
            targetThemeDir: String,
            xmlCandidates: List<String>,
            outputXmlFileName: String
        ): Boolean {
            val zipFile = File(downloadDir, zipFileName)
            return try {
                if (forceRefresh || !zipFile.exists()) {
                    logger.lifecycle("Downloading $themeLabel theme ($version) from $url")
                    val tmp = File(downloadDir, "${zipFile.name}.tmp")
                    URI(url).toURL().openConnection().apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                    }.getInputStream().use { input ->
                        Files.newOutputStream(tmp.toPath()).use { out -> input.copyTo(out) }
                    }
                    if (zipFile.exists()) zipFile.delete()
                    if (!tmp.renameTo(zipFile)) {
                        throw GradleException("Failed to finalize downloaded $themeLabel zip: ${zipFile.absolutePath}")
                    }
                }

                if (expectedSha.isNotEmpty()) {
                    val actual = sha256Hex(zipFile)
                    if (actual != expectedSha) {
                        throw GradleException(
                            "$themeLabel checksum mismatch. expected=$expectedSha actual=$actual file=${zipFile.absolutePath}"
                        )
                    }
                } else {
                    logger.warn("${themeLabel}ThemeSha256 is empty; skipping checksum verification.")
                }

                extractThemeFromZip(
                    zipFile = zipFile,
                    outputRoot = outputRoot,
                    targetThemeDir = targetThemeDir,
                    xmlCandidates = xmlCandidates,
                    outputXmlFileName = outputXmlFileName
                )
                logger.lifecycle(
                    "$themeLabel generated assets ready: ${File(outputRoot, "$targetThemeDir/$outputXmlFileName").absolutePath}"
                )
                true
            } catch (e: Exception) {
                if (allowFallback) {
                    logger.warn("Failed to prepare downloaded $themeLabel theme. Keeping fallback behavior.", e)
                    false
                } else {
                    throw GradleException("Failed to prepare downloaded $themeLabel theme.", e)
                }
            }
        }

        val elevateDownloadEnabled = elevateThemeDownloadEnabled.get().toBoolean()
        val winterDownloadEnabled = elevateWinterThemeDownloadEnabled.get().toBoolean()
        val openHikingDownloadEnabled = openHikingThemeDownloadEnabled.get().toBoolean()
        val embeddedOpenHikingSnapshotPresent = embeddedOpenHikingThemeXml.exists()
        if (!elevateDownloadEnabled && !winterDownloadEnabled && !openHikingDownloadEnabled) {
            logger.lifecycle("All bundled theme downloads disabled; using local app/src/main/assets fallback.")
            return@doLast
        }

        var generatedAnyTheme = false
        var generatedWinterTheme = false

        if (elevateDownloadEnabled) {
            generatedAnyTheme = prepareTheme(
                themeLabel = "Elevate",
                version = elevateThemeVersion.get(),
                url = elevateThemeUrl.get(),
                expectedSha = elevateThemeSha256.get().trim().lowercase(Locale.ROOT),
                forceRefresh = elevateThemeForceRefresh.get().toBoolean(),
                allowFallback = elevateThemeAllowFallback.get().toBoolean(),
                zipFileName = "elevate-${elevateThemeVersion.get()}.zip",
                targetThemeDir = "theme/elevate",
                xmlCandidates = listOf("elevate.xml"),
                outputXmlFileName = "Elevate.xml"
            ) || generatedAnyTheme
        } else {
            logger.lifecycle("Elevate download disabled; relying on local fallback for base Elevate.")
        }

        if (winterDownloadEnabled) {
            generatedWinterTheme = prepareTheme(
                themeLabel = "ElevateWinter",
                version = elevateWinterThemeVersion.get(),
                url = elevateWinterThemeUrl.get(),
                expectedSha = elevateWinterThemeSha256.get().trim().lowercase(Locale.ROOT),
                forceRefresh = elevateWinterThemeForceRefresh.get().toBoolean(),
                allowFallback = elevateWinterThemeAllowFallback.get().toBoolean(),
                zipFileName = "elevate-winter-${elevateWinterThemeVersion.get()}.zip",
                targetThemeDir = "theme/elevate-winter",
                xmlCandidates = listOf("elevate_winter.xml", "elevate.xml"),
                outputXmlFileName = "Elevate.xml"
            )
            generatedAnyTheme = generatedWinterTheme || generatedAnyTheme
        } else {
            logger.lifecycle("Elevate Winter download disabled.")
        }

        if (openHikingDownloadEnabled && !embeddedOpenHikingSnapshotPresent) {
            generatedAnyTheme = prepareTheme(
                themeLabel = "OpenHiking",
                version = openHikingThemeVersion.get(),
                url = openHikingThemeUrl.get(),
                expectedSha = openHikingThemeSha256.get().trim().lowercase(Locale.ROOT),
                forceRefresh = openHikingThemeForceRefresh.get().toBoolean(),
                allowFallback = openHikingThemeAllowFallback.get().toBoolean(),
                zipFileName = "openhiking-${openHikingThemeVersion.get()}.zip",
                targetThemeDir = "theme/openhiking",
                xmlCandidates = listOf("openhiking.xml"),
                outputXmlFileName = "OpenHiking.xml"
            ) || generatedAnyTheme
        } else if (embeddedOpenHikingSnapshotPresent) {
            logger.lifecycle(
                "Embedded OpenHiking snapshot detected at ${embeddedOpenHikingThemeXml.absolutePath}; " +
                    "skipping generated OpenHiking assets to avoid duplicate packaging."
            )
        } else {
            logger.lifecycle("OpenHiking download disabled.")
        }

        if (generatedWinterTheme) {
            val whiteGenerated = createWhiteSkiThemeVariant(
                outputRoot = outputRoot,
                sourceThemeDir = "theme/elevate-winter",
                targetThemeDir = "theme/elevate-winter-white"
            )
            if (whiteGenerated) {
                generatedAnyTheme = true
                logger.lifecycle(
                    "ElevateWinterWhite generated assets ready: " +
                        "${File(outputRoot, "theme/elevate-winter-white/Elevate.xml").absolutePath}"
                )
            } else {
                logger.warn("Failed to generate ElevateWinterWhite variant from ElevateWinter.")
            }
        }

        if (!generatedAnyTheme) {
            logger.lifecycle("No downloaded bundled theme was prepared. Runtime will use local fallback assets.")
        }
    }
}

tasks.register("prepareElevateThemeAssets") {
    group = "build setup"
    description = "Compatibility alias for prepareBundledThemeAssets."
    dependsOn("prepareBundledThemeAssets")
}

tasks.register("prepareOsmPoiIcons") {
    group = "build setup"
    description = "Downloads Maki POI icons into generated assets."
    outputs.dir(generatedOsmPoiIconsAssetsDir)

    doLast {
        val outputRoot = generatedOsmPoiIconsAssetsDir
        outputRoot.mkdirs()

        val downloadEnabled = osmPoiIconsDownloadEnabled.get().toBoolean()
        if (!downloadEnabled) {
            logger.lifecycle("OSM POI icon download disabled.")
            return@doLast
        }

        val allowFallback = osmPoiIconsAllowFallback.get().toBoolean()
        val overwrite = osmPoiIconsOverwrite.get().toBoolean()
        val failOnMissing = osmPoiIconsFailOnMissing.get().toBoolean()
        val makiBaseUrl = makiIconsBaseUrl.get().trim().trimEnd('/')

        val iconSpecs = listOf(
            PoiIconSpec(assetName = "peak.svg", remoteCandidates = listOf("mountain.svg")),
            PoiIconSpec(assetName = "water.svg", remoteCandidates = listOf("drinking-water.svg", "fountain.svg")),
            PoiIconSpec(assetName = "hut.svg", remoteCandidates = listOf("shelter.svg", "lodging.svg")),
            PoiIconSpec(assetName = "camp.svg", remoteCandidates = listOf("campsite.svg", "rv-park.svg")),
            PoiIconSpec(assetName = "food.svg", remoteCandidates = listOf("restaurant.svg", "fast-food.svg", "cafe.svg")),
            PoiIconSpec(assetName = "toilet.svg", remoteCandidates = listOf("toilet.svg")),
            PoiIconSpec(assetName = "transport.svg", remoteCandidates = listOf("bus.svg", "rail.svg", "ferry.svg")),
            PoiIconSpec(assetName = "bike.svg", remoteCandidates = listOf("bicycle-share.svg", "bike-share.svg", "cycling.svg")),
            PoiIconSpec(assetName = "viewpoint.svg", remoteCandidates = listOf("landmark.svg", "triangle.svg")),
            PoiIconSpec(assetName = "parking.svg", remoteCandidates = listOf("parking.svg")),
            PoiIconSpec(assetName = "shop.svg", remoteCandidates = listOf("shop.svg", "grocery.svg")),
            PoiIconSpec(assetName = "generic.svg", remoteCandidates = listOf("marker.svg", "circle.svg"))
        )

        val iconsDir = File(outputRoot, "poi/osm").apply { mkdirs() }
        var downloaded = 0
        var missing = 0

        fun tryDownload(sourceUrl: String, targetFile: File): Boolean {
            val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
            if (tmp.exists()) tmp.delete()
            return try {
                val connection = (URI(sourceUrl).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "GlanceMap-OSMIcons/1.0")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    tmp.delete()
                    return false
                }
                connection.inputStream.use { input ->
                    Files.newOutputStream(tmp.toPath()).use { out -> input.copyTo(out) }
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tmp.renameTo(targetFile)) {
                    throw GradleException("Failed to move temp icon file to ${targetFile.absolutePath}")
                }
                true
            } catch (_: Exception) {
                tmp.delete()
                false
            }
        }

        iconSpecs.forEach { spec ->
            val target = File(iconsDir, spec.assetName)
            if (target.exists() && !overwrite) {
                downloaded += 1
                return@forEach
            }

            val absoluteCandidates = spec.remoteCandidates.filter {
                it.startsWith("http://") || it.startsWith("https://")
            }
            val relativeCandidates = spec.remoteCandidates - absoluteCandidates.toSet()

            val success = absoluteCandidates.any { sourceUrl ->
                tryDownload(sourceUrl, target)
            } || relativeCandidates.any { candidate ->
                tryDownload("$makiBaseUrl/$candidate", target)
            }

            if (success) {
                downloaded += 1
            } else {
                missing += 1
                logger.warn("Missing Maki icon candidates for ${spec.assetName}: ${spec.remoteCandidates}")
            }
        }

        logger.lifecycle("Maki POI icons: downloaded=$downloaded missing=$missing out=${iconsDir.absolutePath}")
        if (missing > 0 && failOnMissing) {
            throw GradleException("Missing $missing Maki POI icons.")
        }
        if (downloaded == 0 && !allowFallback) {
            throw GradleException("No Maki POI icons downloaded and fallback disabled.")
        }
    }
}

tasks.register("prepareLicenseDocsAssets") {
    group = "build setup"
    description = "Copies repository license notices into generated app assets."
    val sourceDirProvider = rootProject.layout.projectDirectory.dir("licenses")
    inputs.files(
        fileTree(sourceDirProvider) {
            include("*.md", "*.txt")
        }
    )
    outputs.dir(generatedLicenseAssetsDir)

    doLast {
        val outputRoot = generatedLicenseAssetsDir
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        val sourceDir = sourceDirProvider.asFile
        if (!sourceDir.exists()) {
            logger.warn("No root licenses directory found at ${sourceDir.absolutePath}.")
            return@doLast
        }

        copy {
            from(sourceDir)
            include("*.md", "*.txt")
            into(File(outputRoot, "licenses"))
        }

        logger.lifecycle("License docs copied to ${File(outputRoot, "licenses").absolutePath}")
    }
}

tasks.register("downloadMapsforgeDem3") {
    group = "map data"
    description = "Download Mapsforge DEM3 (.hgt.zip) tiles from explicit tile ids and/or a bbox."

    doLast {
        val explicitTiles = dem3Tiles.get()
            .split(',', ';', '\n', '\r', '\t', ' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { tile ->
                parseDemTileId(tile) ?: throw GradleException(
                    "Invalid dem3 tile id '$tile'. Expected like N46E006."
                )
            }
            .map { it.id() }
            .toSet()

        val bboxTiles = tilesFromBboxOrThrow(dem3Bbox.get())
        val allTiles = linkedSetOf<String>().apply {
            addAll(explicitTiles)
            addAll(bboxTiles)
        }

        if (allTiles.isEmpty()) {
            throw GradleException(
                "No DEM tiles selected. Set -Pdem3Tiles=N46E006,N46E007 or -Pdem3Bbox=minLat,minLon,maxLat,maxLon"
            )
        }

        val baseUrl = dem3BaseUrl.get().trim().trimEnd('/')
        val overwrite = dem3Overwrite.get().toBoolean()
        val failOnMissing = dem3FailOnMissing.get().toBoolean()
        val outputRootRaw = dem3OutputDirPath.get().trim()
        val outputRoot = if (outputRootRaw.isEmpty()) {
            layout.buildDirectory.dir("generated/dem3").get().asFile
        } else {
            File(outputRootRaw)
        }
        outputRoot.mkdirs()

        logger.lifecycle("Downloading ${allTiles.size} DEM3 tiles into ${outputRoot.absolutePath}")

        val missing = mutableListOf<String>()
        var downloaded = 0
        var skipped = 0

        allTiles.sorted().forEach { tileId ->
            val (folder, fileName) = tilePathSegments(tileId)
            val url = "$baseUrl/$folder/$fileName"
            val localDir = File(outputRoot, folder).apply { mkdirs() }
            val localFile = File(localDir, fileName)

            if (localFile.exists() && !overwrite) {
                skipped += 1
                return@forEach
            }

            val tmpFile = File(localDir, "$fileName.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            try {
                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "GlanceMap-DEM3/1.0")
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw FileNotFoundException("HTTP 404")
                }
                if (code !in 200..299) {
                    throw GradleException("HTTP $code")
                }
                connection.inputStream.use { input ->
                    Files.newOutputStream(tmpFile.toPath()).use { out -> input.copyTo(out) }
                }
                if (localFile.exists()) localFile.delete()
                if (!tmpFile.renameTo(localFile)) {
                    throw GradleException("Failed to move temp file to ${localFile.absolutePath}")
                }
                downloaded += 1
            } catch (e: Exception) {
                tmpFile.delete()
                missing += tileId
                logger.warn("Missing/failed DEM tile $tileId from $url (${e::class.java.simpleName}: ${e.message})")
            }
        }

        logger.lifecycle("DEM download done: downloaded=$downloaded skipped=$skipped missing=${missing.size}")
        if (missing.isNotEmpty()) {
            logger.lifecycle("Missing tiles: ${missing.joinToString(", ")}")
            if (failOnMissing) {
                throw GradleException("Some DEM tiles are missing (${missing.size}).")
            }
        }
    }
}
