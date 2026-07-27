package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GuidanceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstruction
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.RouteInstructionCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class TurnByTurnHapticAlertTrackerTest {
    @Test
    fun turnInsideAdaptiveWindowFiresOnce() {
        val tracker = TurnHapticAlertTracker()
        val sample = sample(instruction = instruction(RouteInstructionCommand.LEFT, 100.0), progressMeters = 92.0)

        val firstEvents = tracker.update(sample)
        val repeatedEvents = tracker.update(sample)

        assertEquals(TurnHapticAlertOutcome.FIRED, firstEvents.single().outcome)
        assertEquals(TurnHapticAlertTrigger.WINDOW, firstEvents.single().trigger)
        assertTrue(repeatedEvents.isEmpty())
    }

    @Test
    fun configuredTurnModeIsReportedAsFiltered() {
        val tracker = TurnHapticAlertTracker()

        val events =
            tracker.update(
                sample(
                    instruction = instruction(RouteInstructionCommand.CONTINUE, 100.0),
                    progressMeters = 95.0,
                    turnAlertsMode = SettingsRepository.TURN_BY_TURN_TURN_ALERTS_IMPORTANT,
                ),
            )

        assertEquals(TurnHapticAlertOutcome.FILTERED, events.single().outcome)
        assertEquals("turn_mode", events.single().reason)
    }

    @Test
    fun allTurnsIncludesSlightTurnsButExcludesContinueAndArrival() {
        val mode = SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL

        assertTrue(shouldAlertForTurn(mode, RouteInstructionCommand.SLIGHT_LEFT))
        assertTrue(shouldAlertForTurn(mode, RouteInstructionCommand.RIGHT))
        assertFalse(shouldAlertForTurn(mode, RouteInstructionCommand.CONTINUE))
        assertFalse(shouldAlertForTurn(mode, RouteInstructionCommand.FINISH))
    }

    @Test
    fun majorTurnsExcludesSlightTurnsAndArrival() {
        val mode = SettingsRepository.TURN_BY_TURN_TURN_ALERTS_IMPORTANT

        assertTrue(shouldAlertForTurn(mode, RouteInstructionCommand.LEFT))
        assertFalse(shouldAlertForTurn(mode, RouteInstructionCommand.SLIGHT_RIGHT))
        assertFalse(shouldAlertForTurn(mode, RouteInstructionCommand.CONTINUE))
        assertFalse(shouldAlertForTurn(mode, RouteInstructionCommand.FINISH))
    }

    @Test
    fun eligibleTurnSuppressedOffRouteIsReportedSeparately() {
        val tracker = TurnHapticAlertTracker()

        val events =
            tracker.update(
                sample(
                    instruction = instruction(RouteInstructionCommand.RIGHT, 100.0),
                    progressMeters = 95.0,
                    offRoute = true,
                ),
            )

        assertEquals(TurnHapticAlertOutcome.OFF_ROUTE, events.single().outcome)
    }

    @Test
    fun gpsJumpAcrossTurnFiresCrossingRecovery() {
        val tracker = TurnHapticAlertTracker()
        tracker.update(
            sample(
                instruction = instruction(RouteInstructionCommand.LEFT, 100.0),
                progressMeters = 70.0,
            ),
        )

        val events =
            tracker.update(
                sample(
                    instruction = instruction(RouteInstructionCommand.RIGHT, 200.0),
                    progressMeters = 110.0,
                ),
            )

        assertEquals(TurnHapticAlertOutcome.FIRED, events.single().outcome)
        assertEquals(TurnHapticAlertTrigger.CROSSING, events.single().trigger)
        assertEquals("gps_crossing_recovery", events.single().reason)
    }

    @Test
    fun excessivelyLateGpsCrossingIsReportedAsMissedWindow() {
        val tracker = TurnHapticAlertTracker()
        tracker.update(
            sample(
                instruction = instruction(RouteInstructionCommand.LEFT, 100.0),
                progressMeters = 70.0,
            ),
        )

        val events =
            tracker.update(
                sample(
                    instruction = instruction(RouteInstructionCommand.RIGHT, 200.0),
                    progressMeters = 170.0,
                ),
            )

        assertEquals(TurnHapticAlertOutcome.MISSED_WINDOW, events.single().outcome)
        assertEquals("recovery_too_late", events.single().reason)
    }

    private fun sample(
        instruction: RouteInstruction,
        progressMeters: Double,
        offRoute: Boolean = false,
        turnAlertsMode: String = SettingsRepository.TURN_BY_TURN_TURN_ALERTS_ALL,
    ): TurnHapticAlertSample =
        TurnHapticAlertSample(
            routeKey = "route",
            active = true,
            mode = GuidanceMode.FOLLOW_ROUTE,
            offRoute = offRoute,
            instruction = instruction,
            distanceToInstructionMeters =
                (instruction.distanceFromStartMeters - progressMeters)
                    .coerceAtLeast(0.0),
            distanceFromStartMeters = progressMeters,
            speedMps = 1.4f,
            activityProfile = SettingsRepository.ACTIVITY_PROFILE_HIKE,
            gpsDeliveryIntervalMs = 3_000L,
            hapticsEnabled = true,
            turnAlertsMode = turnAlertsMode,
        )

    private fun instruction(
        command: RouteInstructionCommand,
        distanceFromStartMeters: Double,
    ): RouteInstruction =
        RouteInstruction(
            command = command,
            message = command.name,
            latLong = LatLong(45.0, 6.0),
            trackPointIndex = distanceFromStartMeters.toInt(),
            distanceFromStartMeters = distanceFromStartMeters,
            turnAngleDegrees = null,
        )
}
