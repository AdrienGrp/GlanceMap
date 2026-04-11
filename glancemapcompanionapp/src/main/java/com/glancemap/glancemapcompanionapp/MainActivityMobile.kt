package com.glancemap.glancemapcompanionapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glancemap.glancemapcompanionapp.filepicker.FilePickerScreen
import com.glancemap.glancemapcompanionapp.ui.theme.GlanceMapTheme

class MainActivityMobile : ComponentActivity() {

    private var incomingUris by mutableStateOf<List<Uri>>(emptyList())
    private var incomingIntentToken by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateIncomingSelection(intent)

        setContent {
            GlanceMapTheme {
                val vm: FileTransferViewModel = viewModel()

                LaunchedEffect(incomingIntentToken) {
                    val uris = incomingUris
                    if (uris.isEmpty()) return@LaunchedEffect

                    uris.forEach { uri ->
                        try {
                            val takeFlags = (
                                intent.flags and
                                    (
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                        )
                                )
                            if (takeFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        } catch (_: Exception) {
                            // Ignore providers that do not expose persistable permissions.
                        }
                    }

                    vm.loadFilesFromUris(this@MainActivityMobile, uris)
                }

                FilePickerScreen(viewModel = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateIncomingSelection(intent)
    }

    private fun updateIncomingSelection(intent: Intent?) {
        val uris = extractIncomingUris(intent)
        if (uris.isEmpty()) return
        incomingUris = uris
        incomingIntentToken = SystemClock.elapsedRealtimeNanos()
    }

    private fun extractIncomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val uris = LinkedHashSet<Uri>()
        intent.data?.let(uris::add)

        val stream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        when (stream) {
            null -> Unit
            else -> uris += stream
        }

        val extraStreams = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
        }.getOrNull().orEmpty()
        uris += extraStreams

        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let(uris::add)
            }
        }

        return uris.toList()
    }
}
