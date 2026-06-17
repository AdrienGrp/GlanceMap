package com.glancemap.glancemapwearos.presentation.features.navigate

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingViewModel
import kotlinx.coroutines.delay

@Composable
internal fun rememberRecordingStatusMessage(
    state: TraceRecordingUiState,
    traceRecordingViewModel: TraceRecordingViewModel,
): String? {
    var recordingStatusMessage by remember { mutableStateOf<String?>(null) }
    var recordingStatusMessageToken by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.message) {
        state.message
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                recordingStatusMessage = message
                recordingStatusMessageToken = SystemClock.elapsedRealtime()
                traceRecordingViewModel.consumeMessage(message)
            }
    }
    LaunchedEffect(recordingStatusMessageToken) {
        if (recordingStatusMessageToken != 0L && recordingStatusMessage != null) {
            val token = recordingStatusMessageToken
            delay(RECORDING_STATUS_MESSAGE_DURATION_MS)
            if (recordingStatusMessageToken == token) {
                recordingStatusMessage = null
            }
        }
    }
    return recordingStatusMessage
}

private const val RECORDING_STATUS_MESSAGE_DURATION_MS = 1_200L
