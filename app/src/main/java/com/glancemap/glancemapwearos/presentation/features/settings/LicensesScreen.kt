package com.glancemap.glancemapwearos.presentation.features.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.WearInfoDialog
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Chip

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun LicensesScreen(onOpenGeneralSettings: () -> Unit) {
    val appVersionLabel = rememberAppVersionLabel()
    val listTokens =
        rememberSettingsListTokens(
            compactTop = 24.dp,
            standardTop = 28.dp,
            expandedTop = 32.dp,
            compactBottom = 56.dp,
            standardBottom = 60.dp,
            expandedBottom = 68.dp,
        )
    var selectedDocument by remember { mutableStateOf<LicenseDocument?>(null) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            Text(
                text = "Credits & Legal",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = "Thanks to OpenAndroMaps, Elevate, OpenHiking, Tiramisu, Hike, Ride & Sight, OpenStreetMap, Refuges.info, Overpass, Mapsforge and BRouter.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Privacy Policy",
                secondaryLabel = "Data access, sharing and retention",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Privacy Policy",
                            assetPath = "licenses/PRIVACY_POLICY.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Safety & Limits",
                secondaryLabel = "Map/theme errors and personal responsibility",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Safety & Limitations",
                            assetPath = "licenses/SAFETY_AND_LIMITATIONS.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Credits & Thanks",
                secondaryLabel = "Main contributors and projects",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Credits & Thanks",
                            assetPath = "licenses/CREDITS_AND_THANKS.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "AI Acknowledgment",
                secondaryLabel = "Human creators and transparency",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "AI & Creator Acknowledgment",
                            assetPath = "licenses/AI_ACKNOWLEDGEMENT.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Companion Sources",
                secondaryLabel = "Map, GPX and refuge websites",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Companion External Sources",
                            assetPath = "licenses/COMPANION_EXTERNAL_SOURCES.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Compliance Status",
                secondaryLabel = "Release checklist and pending items",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Compliance Status",
                            assetPath = "licenses/COMPLIANCE_STATUS.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Open Source Notices",
                secondaryLabel = "Libraries and OSS licenses",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Open Source Notices",
                            assetPath = "licenses/THIRD_PARTY_NOTICES.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "OpenHiking Theme",
                secondaryLabel = "Bundled hiking theme details",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "OpenHiking Theme",
                            assetPath = "licenses/OPENHIKING_THEME.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "French Kiss Theme",
                secondaryLabel = "Bundled IGN-style theme details",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "French Kiss Theme",
                            assetPath = "licenses/FRENCH_KISS_THEME.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Tiramisu Theme",
                secondaryLabel = "Bundled cycle/hike theme details",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Tiramisu Theme",
                            assetPath = "licenses/TIRAMISU_THEME.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Hike, Ride & Sight",
                secondaryLabel = "Bundled overlay-rich theme details",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Hike, Ride & Sight Theme",
                            assetPath = "licenses/HIKE_RIDE_SIGHT_THEME.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Voluntary Theme",
                secondaryLabel = "Bundled OS-inspired theme details",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Voluntary Theme",
                            assetPath = "licenses/VOLUNTARY_THEME.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Data & Asset Attribution",
                secondaryLabel = "OSM, Elevate, bundled themes, DEM, icons",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Data & Asset Attribution",
                            assetPath = "licenses/DATA_AND_ASSET_ATTRIBUTION.md",
                        )
                },
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = "Service Terms & API Usage",
                secondaryLabel = "Provider terms and usage limits",
                onClick = {
                    selectedDocument =
                        LicenseDocument(
                            title = "Service Terms & API Usage",
                            assetPath = "licenses/SERVICE_TERMS_AND_API_USAGE.md",
                        )
                },
            )
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    selectedDocument?.let { document ->
        LicenseDocumentDialog(
            document = document,
            onDismiss = { selectedDocument = null },
        )
    }
}

private data class LicenseDocument(
    val title: String,
    val assetPath: String,
)

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun LicenseDocumentDialog(
    document: LicenseDocument,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val documentText =
        remember(document.assetPath) {
            loadTextAsset(context, document.assetPath)
        }

    WearInfoDialog(
        visible = true,
        title = document.title,
        onDismiss = onDismiss,
    ) {
        item {
            Text(
                text = documentText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun rememberAppVersionLabel(): String {
    val context = LocalContext.current
    return remember(context) {
        buildAppVersionLabel(context)
    }
}

@Suppress("DEPRECATION")
private fun buildAppVersionLabel(context: Context): String =
    runCatching {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                0,
            )
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        "Version $versionName ($versionCode)"
    }.getOrElse {
        "Version unknown"
    }

private fun loadTextAsset(
    context: Context,
    assetPath: String,
): String =
    runCatching {
        context.assets
            .open(assetPath)
            .bufferedReader()
            .use { it.readText() }
    }.getOrElse {
        "Unable to load: $assetPath"
    }
