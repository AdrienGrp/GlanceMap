package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSensorLifecycleTest {
    @Test
    fun reconnectBackoffBecomesLessAggressiveAfterRepeatedFailures() {
        assertEquals(2_000L, externalSensorReconnectDelayMs(1))
        assertEquals(5_000L, externalSensorReconnectDelayMs(2))
        assertEquals(10_000L, externalSensorReconnectDelayMs(3))
        assertEquals(30_000L, externalSensorReconnectDelayMs(4))
        assertEquals(60_000L, externalSensorReconnectDelayMs(5))
        assertEquals(60_000L, externalSensorReconnectDelayMs(20))
    }

    @Test
    fun telemetryKeepsInitialContextThenSamplesPeriodicUpdates() {
        assertTrue((1..5).all(::shouldLogExternalSensorSample))
        assertTrue(shouldLogExternalSensorSample(30))
        assertTrue(shouldLogExternalSensorSample(60))
        assertEquals(false, shouldLogExternalSensorSample(6))
        assertEquals(false, shouldLogExternalSensorSample(31))
    }

    @Test
    fun notificationPropertyUsesNotificationDescriptorValue() {
        assertArrayEquals(
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            notificationDescriptorValue(BluetoothGattCharacteristic.PROPERTY_NOTIFY),
        )
    }

    @Test
    fun indicationOnlyPropertyUsesIndicationDescriptorValue() {
        assertArrayEquals(
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
            notificationDescriptorValue(BluetoothGattCharacteristic.PROPERTY_INDICATE),
        )
    }

    @Test
    fun unsupportedMeasurementPropertyCannotBeSubscribed() {
        assertNull(notificationDescriptorValue(BluetoothGattCharacteristic.PROPERTY_READ))
    }

    @Test
    fun cyclingPowerIsRecognizedAsSupportedSensorKind() {
        assertEquals(
            BluetoothUuid.service16(0x1818),
            ExternalSensorKind.CYCLING_POWER.serviceUuid,
        )
    }
}
