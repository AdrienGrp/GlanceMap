package com.glancemap.glancemapcompanionapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.glancemap.glancemapcompanionapp.ui.theme.GlanceMapTheme
import com.glancemap.shared.transfer.TransferDataLayerContract

class PrivacyPolicyActivity : ComponentActivity() {
    companion object {
        fun creditsAndLegalIntent(context: Context): Intent = Intent(context, PrivacyPolicyActivity::class.java)

        fun privacyPolicyIntent(context: Context): Intent =
            documentIntent(
                context = context,
                document = COMPANION_CREDITS_AND_LEGAL_DOCUMENTS.first(),
            )

        fun creditsIntent(context: Context): Intent =
            documentIntent(
                context = context,
                document = COMPANION_CREDITS_AND_LEGAL_DOCUMENTS.first { it.assetPath == CREDITS_AND_THANKS_ASSET_PATH },
            )

        private fun documentIntent(
            context: Context,
            document: LegalDocument,
        ): Intent =
            Intent(context, PrivacyPolicyActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_BUTTON_LABEL, document.buttonLabel)
                putExtra(EXTRA_DOCUMENT_TITLE, document.documentTitle)
                putExtra(EXTRA_DOCUMENT_SECONDARY_LABEL, document.secondaryLabel)
                putExtra(EXTRA_DOCUMENT_ASSET_PATH, document.assetPath)
                putExtra(EXTRA_SHOW_PRIVACY_CONTACT, document.showPrivacyContact)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val document = intent.toLegalDocumentOrNull()
        title = document?.documentTitle ?: "Credits & Legal"

        setContent {
            GlanceMapTheme {
                if (document == null) {
                    CreditsAndLegalScreen(
                        onBack = ::finish,
                        onOpenDocument = { selected ->
                            startActivity(documentIntent(this, selected))
                        },
                    )
                } else {
                    LegalDocumentScreen(
                        document = document,
                        onBack = ::finish,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsAndLegalScreen(
    onBack: () -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
) {
    val versionLabel = rememberAppVersionLabel()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Credits & Legal",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CreditsAndLegalIntro(versionLabel = versionLabel)
            COMPANION_CREDITS_AND_LEGAL_DOCUMENTS.forEach { document ->
                OutlinedButton(
                    onClick = { onOpenDocument(document) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = document.buttonLabel,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = document.secondaryLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CreditsAndLegalIntro(versionLabel: String) {
    Text(
        text = "Thanks to OpenAndroMaps, Elevate, OpenHiking, Tiramisu, Hike, Ride & Sight, OpenStreetMap, Refuges.info, Overpass, Mapsforge and BRouter.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = versionLabel,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LegalDocumentScreen(
    document: LegalDocument,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val documentText =
        remember(document.assetPath) {
            loadAssetDocumentText(
                context = context,
                assetPath = document.assetPath,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = document.documentTitle,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = document.secondaryLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer {
                Text(
                    text = documentText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (document.showPrivacyContact) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { openPrivacyContactEmail(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Email privacy contact")
                }
            }
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

private fun loadAssetDocumentText(
    context: Context,
    assetPath: String,
): String =
    runCatching {
        context.assets
            .open(assetPath)
            .bufferedReader()
            .use { it.readText() }
    }.getOrElse {
        "Unable to load this document."
    }

private fun openPrivacyContactEmail(context: Context) {
    val intent =
        Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:${TransferDataLayerContract.DIAGNOSTICS_SUPPORT_EMAIL}"),
        ).apply {
            putExtra(Intent.EXTRA_SUBJECT, "GlanceMap privacy question")
        }

    runCatching {
        context.startActivity(intent)
    }.recoverCatching {
        throw ActivityNotFoundException("No email app available")
    }
}

private data class LegalDocument(
    val buttonLabel: String,
    val documentTitle: String,
    val secondaryLabel: String,
    val assetPath: String,
    val showPrivacyContact: Boolean,
)

private fun Intent.toLegalDocumentOrNull(): LegalDocument? {
    val assetPath = getStringExtra(EXTRA_DOCUMENT_ASSET_PATH).orEmpty()
    if (assetPath.isBlank()) return null
    val buttonLabel = getStringExtra(EXTRA_DOCUMENT_BUTTON_LABEL).orEmpty().ifBlank { "Privacy Policy" }
    val documentTitle = getStringExtra(EXTRA_DOCUMENT_TITLE).orEmpty().ifBlank { buttonLabel }
    val secondaryLabel =
        getStringExtra(EXTRA_DOCUMENT_SECONDARY_LABEL)
            .orEmpty()
            .ifBlank { "Data access, sharing and retention" }
    return LegalDocument(
        buttonLabel = buttonLabel,
        documentTitle = documentTitle,
        secondaryLabel = secondaryLabel,
        assetPath = assetPath,
        showPrivacyContact = getBooleanExtra(EXTRA_SHOW_PRIVACY_CONTACT, false),
    )
}

private const val PRIVACY_POLICY_ASSET_PATH = "PRIVACY_POLICY.md"
private const val CREDITS_AND_THANKS_ASSET_PATH = "CREDITS_AND_THANKS.md"
private const val EXTRA_DOCUMENT_BUTTON_LABEL = "document_button_label"
private const val EXTRA_DOCUMENT_TITLE = "document_title"
private const val EXTRA_DOCUMENT_SECONDARY_LABEL = "document_secondary_label"
private const val EXTRA_DOCUMENT_ASSET_PATH = "document_asset_path"
private const val EXTRA_SHOW_PRIVACY_CONTACT = "show_privacy_contact"
private const val SAFETY_AND_LIMITATIONS_ASSET_PATH = "SAFETY_AND_LIMITATIONS.md"
private const val AI_ACKNOWLEDGEMENT_ASSET_PATH = "AI_ACKNOWLEDGEMENT.md"
private const val COMPANION_EXTERNAL_SOURCES_ASSET_PATH = "COMPANION_EXTERNAL_SOURCES.md"
private const val COMPLIANCE_STATUS_ASSET_PATH = "COMPLIANCE_STATUS.md"
private const val THIRD_PARTY_NOTICES_ASSET_PATH = "THIRD_PARTY_NOTICES.md"
private const val OPENHIKING_THEME_ASSET_PATH = "OPENHIKING_THEME.md"
private const val FRENCH_KISS_THEME_ASSET_PATH = "FRENCH_KISS_THEME.md"
private const val TIRAMISU_THEME_ASSET_PATH = "TIRAMISU_THEME.md"
private const val HIKE_RIDE_SIGHT_THEME_ASSET_PATH = "HIKE_RIDE_SIGHT_THEME.md"
private const val VOLUNTARY_THEME_ASSET_PATH = "VOLUNTARY_THEME.md"
private const val DATA_AND_ASSET_ATTRIBUTION_ASSET_PATH = "DATA_AND_ASSET_ATTRIBUTION.md"
private const val SERVICE_TERMS_AND_API_USAGE_ASSET_PATH = "SERVICE_TERMS_AND_API_USAGE.md"

private val COMPANION_CREDITS_AND_LEGAL_DOCUMENTS =
    listOf(
        LegalDocument(
            buttonLabel = "Privacy Policy",
            documentTitle = "Privacy Policy",
            secondaryLabel = "Data access, sharing and retention",
            assetPath = PRIVACY_POLICY_ASSET_PATH,
            showPrivacyContact = true,
        ),
        LegalDocument(
            buttonLabel = "Safety & Limits",
            documentTitle = "Safety & Limitations",
            secondaryLabel = "Map/theme errors and personal responsibility",
            assetPath = SAFETY_AND_LIMITATIONS_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Credits & Thanks",
            documentTitle = "Credits & Thanks",
            secondaryLabel = "Main contributors and projects",
            assetPath = CREDITS_AND_THANKS_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "AI Acknowledgment",
            documentTitle = "AI & Creator Acknowledgment",
            secondaryLabel = "Human creators and transparency",
            assetPath = AI_ACKNOWLEDGEMENT_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Companion Sources",
            documentTitle = "Companion External Sources",
            secondaryLabel = "Map, GPX and refuge websites",
            assetPath = COMPANION_EXTERNAL_SOURCES_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Compliance Status",
            documentTitle = "Compliance Status",
            secondaryLabel = "Release checklist and pending items",
            assetPath = COMPLIANCE_STATUS_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Open Source Notices",
            documentTitle = "Open Source Notices",
            secondaryLabel = "Libraries and OSS licenses",
            assetPath = THIRD_PARTY_NOTICES_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "OpenHiking Theme",
            documentTitle = "OpenHiking Theme",
            secondaryLabel = "Bundled hiking theme details",
            assetPath = OPENHIKING_THEME_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "French Kiss Theme",
            documentTitle = "French Kiss Theme",
            secondaryLabel = "Bundled IGN-style theme details",
            assetPath = FRENCH_KISS_THEME_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Tiramisu Theme",
            documentTitle = "Tiramisu Theme",
            secondaryLabel = "Bundled cycle/hike theme details",
            assetPath = TIRAMISU_THEME_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Hike, Ride & Sight",
            documentTitle = "Hike, Ride & Sight Theme",
            secondaryLabel = "Bundled overlay-rich theme details",
            assetPath = HIKE_RIDE_SIGHT_THEME_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Voluntary Theme",
            documentTitle = "Voluntary Theme",
            secondaryLabel = "Bundled OS-inspired theme details",
            assetPath = VOLUNTARY_THEME_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Data & Asset Attribution",
            documentTitle = "Data & Asset Attribution",
            secondaryLabel = "OSM, Elevate, bundled themes, DEM, icons",
            assetPath = DATA_AND_ASSET_ATTRIBUTION_ASSET_PATH,
            showPrivacyContact = false,
        ),
        LegalDocument(
            buttonLabel = "Service Terms & API Usage",
            documentTitle = "Service Terms & API Usage",
            secondaryLabel = "Provider terms and usage limits",
            assetPath = SERVICE_TERMS_AND_API_USAGE_ASSET_PATH,
            showPrivacyContact = false,
        ),
    )
