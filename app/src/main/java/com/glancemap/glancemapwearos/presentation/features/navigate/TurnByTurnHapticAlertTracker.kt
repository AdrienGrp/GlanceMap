package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstruction
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand

internal enum class TurnHapticAlertOutcome(
    val telemetryValue: String,
) {
    FIRED("fired"),
    FILTERED("filtered"),
    OFF_ROUTE("off_route"),
    MISSED_WINDOW("missed_window"),
}

internal enum class TurnHapticAlertTrigger(
    val telemetryValue: String,
) {
    WINDOW("window"),
    CROSSING("crossing"),
}

internal data class TurnHapticAlertSample(
    val routeKey: String,
    val active: Boolean,
    val mode: GuidanceMode,
    val offRoute: Boolean,
    val instruction: RouteInstruction?,
    val distanceToInstructionMeters: Double?,
    val distanceFromStartMeters: Double?,
    val speedMps: Float?,
    val activityProfile: String,
    val gpsDeliveryIntervalMs: Long,
    val hapticsEnabled: Boolean,
    val turnAlertsMode: String,
)

internal data class TurnHapticAlertEvent(
    val instructionKey: String,
    val outcome: TurnHapticAlertOutcome,
    val trigger: TurnHapticAlertTrigger?,
    val instruction: RouteInstruction,
    val distanceMeters: Double,
    val alertDistanceMeters: Double,
    val previousDistanceMeters: Double? = null,
    val overshootMeters: Double? = null,
    val reason: String? = null,
    val speedMps: Float?,
    val activityProfile: String,
    val turnAlertsMode: String,
    val gpsDeliveryIntervalMs: Long,
)

internal class TurnHapticAlertTracker {
    private var previousObservation: TurnHapticObservation? = null
    private val terminalInstructionKeys = mutableSetOf<String>()
    private val lastOutcomeSignatures = mutableMapOf<String, String>()

    fun update(sample: TurnHapticAlertSample): List<TurnHapticAlertEvent> {
        if (!sample.active) {
            reset()
            return emptyList()
        }

        val currentObservation = sample.toObservation()
        val events = mutableListOf<TurnHapticAlertEvent>()
        crossingEvent(
            previous = previousObservation,
            current = currentObservation,
            sample = sample,
        )?.let(events::add)
        windowEvent(current = currentObservation, sample = sample)?.let(events::add)
        previousObservation = currentObservation
        return events.mapNotNull(::recordIfNew)
    }

    private fun crossingEvent(
        previous: TurnHapticObservation?,
        current: TurnHapticObservation?,
        sample: TurnHapticAlertSample,
    ): TurnHapticAlertEvent? =
        previous
            ?.takeIf { it.wasSkippedBy(current, sample) }
            ?.takeUnless { it.instructionKey in terminalInstructionKeys }
            ?.let { crossedObservation ->
                buildCrossingEvent(
                    crossedObservation = crossedObservation,
                    current = current,
                    sample = sample,
                )
            }

    private fun buildCrossingEvent(
        crossedObservation: TurnHapticObservation,
        current: TurnHapticObservation?,
        sample: TurnHapticAlertSample,
    ): TurnHapticAlertEvent? {
        val block = alertBlock(sample, crossedObservation.instruction.command)
        val routeProgressMeters = sample.distanceFromStartMeters
        return when {
            block != null ->
                crossedObservation.event(
                    outcome = block.outcome,
                    trigger = TurnHapticAlertTrigger.CROSSING,
                    reason = block.reason,
                )
            routeProgressMeters != null ->
                eligibleCrossingEvent(
                    crossedObservation = crossedObservation,
                    current = current,
                    sample = sample,
                    routeProgressMeters = routeProgressMeters,
                )
            else -> null
        }
    }

    private fun eligibleCrossingEvent(
        crossedObservation: TurnHapticObservation,
        current: TurnHapticObservation?,
        sample: TurnHapticAlertSample,
        routeProgressMeters: Double,
    ): TurnHapticAlertEvent {
        val overshootMeters =
            (routeProgressMeters - crossedObservation.instruction.distanceFromStartMeters)
                .coerceAtLeast(0.0)
        val currentTurnNeedsAlert =
            current?.insideWindow == true &&
                alertBlock(sample, current.instruction.command) == null
        val recoveryDistanceMeters =
            turnHapticCrossingRecoveryMeters(
                speedMps = sample.speedMps,
                activityProfile = sample.activityProfile,
                gpsDeliveryIntervalMs = sample.gpsDeliveryIntervalMs,
            )
        return when {
            currentTurnNeedsAlert ->
                crossedObservation.event(
                    outcome = TurnHapticAlertOutcome.MISSED_WINDOW,
                    trigger = TurnHapticAlertTrigger.CROSSING,
                    overshootMeters = overshootMeters,
                    reason = "next_turn_due",
                )
            overshootMeters <= recoveryDistanceMeters ->
                crossedObservation.event(
                    outcome = TurnHapticAlertOutcome.FIRED,
                    trigger = TurnHapticAlertTrigger.CROSSING,
                    overshootMeters = overshootMeters,
                    reason = "gps_crossing_recovery",
                )
            else ->
                crossedObservation.event(
                    outcome = TurnHapticAlertOutcome.MISSED_WINDOW,
                    trigger = TurnHapticAlertTrigger.CROSSING,
                    overshootMeters = overshootMeters,
                    reason = "recovery_too_late",
                )
        }
    }

    private fun windowEvent(
        current: TurnHapticObservation?,
        sample: TurnHapticAlertSample,
    ): TurnHapticAlertEvent? =
        current
            ?.takeIf { it.insideWindow }
            ?.takeUnless { it.instructionKey in terminalInstructionKeys }
            ?.let { dueObservation ->
                val block = alertBlock(sample, dueObservation.instruction.command)
                dueObservation.event(
                    outcome = block?.outcome ?: TurnHapticAlertOutcome.FIRED,
                    trigger = TurnHapticAlertTrigger.WINDOW,
                    reason = block?.reason,
                )
            }

    private fun alertBlock(
        sample: TurnHapticAlertSample,
        command: RouteInstructionCommand,
    ): TurnHapticAlertBlock? =
        when {
            !sample.hapticsEnabled ->
                TurnHapticAlertBlock(TurnHapticAlertOutcome.FILTERED, "haptics_disabled")
            !shouldAlertForTurn(sample.turnAlertsMode, command) ->
                TurnHapticAlertBlock(TurnHapticAlertOutcome.FILTERED, "turn_mode")
            sample.mode != GuidanceMode.FOLLOW_ROUTE ->
                TurnHapticAlertBlock(TurnHapticAlertOutcome.FILTERED, "guidance_mode")
            sample.offRoute ->
                TurnHapticAlertBlock(TurnHapticAlertOutcome.OFF_ROUTE, "off_route")
            else -> null
        }

    private fun recordIfNew(event: TurnHapticAlertEvent): TurnHapticAlertEvent? {
        val signature = "${event.outcome}:${event.trigger}:${event.reason}"
        if (lastOutcomeSignatures[event.instructionKey] == signature) return null
        lastOutcomeSignatures[event.instructionKey] = signature
        if (event.outcome == TurnHapticAlertOutcome.FIRED || event.outcome == TurnHapticAlertOutcome.MISSED_WINDOW) {
            terminalInstructionKeys += event.instructionKey
        }
        return event
    }

    private fun reset() {
        previousObservation = null
        terminalInstructionKeys.clear()
        lastOutcomeSignatures.clear()
    }
}

private data class TurnHapticObservation(
    val instructionKey: String,
    val instruction: RouteInstruction,
    val distanceMeters: Double,
    val alertDistanceMeters: Double,
    val speedMps: Float?,
    val activityProfile: String,
    val turnAlertsMode: String,
    val gpsDeliveryIntervalMs: Long,
) {
    val insideWindow: Boolean = distanceMeters <= alertDistanceMeters

    fun wasSkippedBy(
        current: TurnHapticObservation?,
        sample: TurnHapticAlertSample,
    ): Boolean {
        val instructionChanged = instructionKey != current?.instructionKey
        val routeProgressMeters = sample.distanceFromStartMeters?.takeIf(Double::isFinite)
        val crossedInstruction = routeProgressMeters?.let { it >= instruction.distanceFromStartMeters } == true
        return instructionChanged && !insideWindow && crossedInstruction
    }

    fun event(
        outcome: TurnHapticAlertOutcome,
        trigger: TurnHapticAlertTrigger,
        overshootMeters: Double? = null,
        reason: String? = null,
    ): TurnHapticAlertEvent =
        TurnHapticAlertEvent(
            instructionKey = instructionKey,
            outcome = outcome,
            trigger = trigger,
            instruction = instruction,
            distanceMeters = if (trigger == TurnHapticAlertTrigger.CROSSING) 0.0 else distanceMeters,
            alertDistanceMeters = alertDistanceMeters,
            previousDistanceMeters = distanceMeters.takeIf { trigger == TurnHapticAlertTrigger.CROSSING },
            overshootMeters = overshootMeters,
            reason = reason,
            speedMps = speedMps,
            activityProfile = activityProfile,
            turnAlertsMode = turnAlertsMode,
            gpsDeliveryIntervalMs = gpsDeliveryIntervalMs,
        )
}

private data class TurnHapticAlertBlock(
    val outcome: TurnHapticAlertOutcome,
    val reason: String,
)

private fun TurnHapticAlertSample.toObservation(): TurnHapticObservation? {
    val currentInstruction = instruction
    val distanceMeters = distanceToInstructionMeters?.takeIf(Double::isFinite)
    return if (currentInstruction != null && distanceMeters != null) {
        val alertDistanceMeters =
            turnHapticDistanceMeters(
                speedMps = speedMps,
                activityProfile = activityProfile,
                gpsDeliveryIntervalMs = gpsDeliveryIntervalMs,
            )
        TurnHapticObservation(
            instructionKey = currentInstruction.instructionKey(routeKey),
            instruction = currentInstruction,
            distanceMeters = distanceMeters,
            alertDistanceMeters = alertDistanceMeters,
            speedMps = speedMps,
            activityProfile = activityProfile,
            turnAlertsMode = turnAlertsMode,
            gpsDeliveryIntervalMs = gpsDeliveryIntervalMs,
        )
    } else {
        null
    }
}

private fun RouteInstruction.instructionKey(routeKey: String): String = "$routeKey:$trackPointIndex:$command"

internal fun shouldAlertForTurn(
    mode: String,
    command: RouteInstructionCommand,
): Boolean =
    when (mode) {
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_OFF -> false
        SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL -> true
        else ->
            command != RouteInstructionCommand.CONTINUE &&
                command != RouteInstructionCommand.SLIGHT_LEFT &&
                command != RouteInstructionCommand.SLIGHT_RIGHT
    }
