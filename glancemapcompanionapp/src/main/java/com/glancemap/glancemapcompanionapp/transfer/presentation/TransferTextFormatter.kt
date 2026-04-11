package com.glancemap.glancemapcompanionapp.transfer.presentation

internal data class NotificationTextPresentation(
    val title: String,
    val contentText: String,
    val expandedText: String,
    val subText: String?,
    val waitingForReconnect: Boolean,
)

internal object TransferTextFormatter {
    fun formatCardText(
        rawProgressText: String,
        statusMessage: String,
        isPaused: Boolean,
        canResume: Boolean,
        showTechnicalDetails: Boolean,
    ): String {
        val normalized = normalizedText(rawProgressText, statusMessage)
        if (showTechnicalDetails) {
            return normalized
        }
        val simpleStatus =
            simpleStatus(
                rawText = normalized,
                statusMessage = statusMessage,
                isPaused = isPaused,
                canResume = canResume,
            )
        val fileLine = extractFileLine(normalized)
        val progressLine = extractProgressLine(normalized)
        return listOfNotNull(fileLine, simpleStatus, progressLine)
            .joinToString("\n")
            .ifBlank { simpleStatus }
    }

    fun buildNotificationPresentation(
        rawText: String,
        progress: Int,
        isPaused: Boolean,
        showTechnicalDetails: Boolean,
    ): NotificationTextPresentation {
        val normalized = normalizedText(rawText, rawText)
        if (showTechnicalDetails) {
            val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val primaryLine = lines.firstOrNull().orEmpty()
            val detailLine = lines.drop(1).joinToString(" • ").ifBlank { primaryLine.ifBlank { normalized } }
            val expandedText =
                if (primaryLine.isNotBlank() && primaryLine != detailLine) {
                    "$primaryLine\n$detailLine"
                } else {
                    detailLine
                }
            val waitingForReconnect = normalized.contains("Waiting for watch reconnect", ignoreCase = true)
            val title =
                when {
                    waitingForReconnect && progress < 0 -> "Waiting for watch reconnect…"
                    waitingForReconnect -> "Waiting for watch reconnect… ${progress.coerceIn(0, 100)}%"
                    progress < 0 -> "Sending file to watch…"
                    else -> "Sending file to watch… ${progress.coerceIn(0, 100)}%"
                }
            return NotificationTextPresentation(
                title = title,
                contentText = detailLine,
                expandedText = expandedText,
                subText = if (primaryLine.isNotBlank() && primaryLine != detailLine) primaryLine else null,
                waitingForReconnect = waitingForReconnect,
            )
        }
        val simpleStatus =
            simpleStatus(
                rawText = normalized,
                statusMessage = normalized,
                isPaused = isPaused,
                canResume = normalized.contains("paused by user", ignoreCase = true),
            )
        val fileLine = extractFileLine(normalized)
        val progressLine = extractProgressLine(normalized)
        val title =
            if (progress < 0) {
                simpleStatus
            } else {
                "$simpleStatus ${progress.coerceIn(0, 100)}%"
            }
        val contentText = progressLine ?: fileLine ?: simpleStatus.removeSuffix("…")
        val expandedText =
            listOfNotNull(fileLine, simpleStatus, progressLine)
                .joinToString("\n")
                .ifBlank { simpleStatus }

        return NotificationTextPresentation(
            title = title,
            contentText = contentText,
            expandedText = expandedText,
            subText =
                when {
                    fileLine != null && progressLine != null -> fileLine
                    fileLine != null -> simpleStatus
                    else -> null
                },
            waitingForReconnect = isWaitingText(normalized),
        )
    }

    private fun normalizedText(
        primary: String,
        secondary: String,
    ): String = primary.trim().ifBlank { secondary.trim() }.ifBlank { "Sending…" }

    private fun simpleStatus(
        rawText: String,
        statusMessage: String,
        isPaused: Boolean,
        canResume: Boolean,
    ): String {
        val combined =
            listOf(rawText, statusMessage)
                .joinToString("\n")
                .lowercase()

        return when {
            combined.contains("cancelling") || combined.contains("stopping current transfer") -> "Stopping…"
            combined.contains("validating checksum") ||
                combined.contains("verifying") ||
                combined.contains("checking partial file") ||
                combined.contains("repairing partial file") ||
                combined.contains("preparing checksum") -> "Verifying…"
            combined.contains("checking existing files on watch") ||
                combined.contains("connecting to phone") ||
                combined.contains("starting") ||
                combined.contains("preparing") -> "Preparing…"
            isWaitingText(combined) -> "Waiting…"
            isPaused && canResume -> "Paused"
            isPaused -> "Waiting…"
            combined.contains("resuming") -> "Sending…"
            else -> "Sending…"
        }
    }

    private fun isWaitingText(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("waiting for watch reconnect") ||
            normalized.contains("waiting to resume") ||
            normalized.contains("waiting for wi-fi") ||
            normalized.contains("network lost") ||
            normalized.contains("connection interrupted")
    }

    private fun extractFileLine(text: String): String? =
        text
            .lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("File ", ignoreCase = true) }

    private fun extractProgressLine(text: String): String? =
        text
            .lines()
            .map { it.trim() }
            .lastOrNull {
                it.startsWith("HTTP:", ignoreCase = true) ||
                    it.startsWith("Channel:", ignoreCase = true) ||
                    it.startsWith("Message:", ignoreCase = true)
            }
}
