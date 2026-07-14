package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertRecipientTest {
    @Test
    fun normalizesEmailAndPhoneAlertRecipients() {
        assertEquals(
            AlertRecipient("alerts@example.com", AlertRecipientType.EMAIL),
            normalizedAlertRecipient(" Alerts@Example.com "),
        )
        assertEquals(
            AlertRecipient("+33612345678", AlertRecipientType.SMS),
            normalizedAlertRecipient("+33 6 12-34.56/78"),
        )
    }

    @Test
    fun rejectsPhoneNumbersWithoutAnInternationalPrefix() {
        assertNull(normalizedAlertRecipient("0612345678"))
        assertNull(normalizedAlertRecipient("+33,612345678"))
        assertNull(normalizedAlertRecipient("+"))
    }

    @Test
    fun sendsEmailsAndNormalizedSmsRecipientsInTheAlertParameter() {
        val recipients = listOf("alerts@example.com", "+33 6 12 34 56 78")

        assertEquals(
            "alerts@example.com,+33612345678,+41791234567",
            alertRecipientsForRequest(recipients, "+41 79 123 45 67"),
        )
        assertEquals(
            listOf("+33612345678", "+41791234567"),
            smsAlertRecipients(recipients, "+41 79 123 45 67"),
        )
    }

    @Test
    fun resolvesPendingInputsIntoTheSavedRecipientLists() {
        assertEquals(
            listOf("first@example.com", "second@example.com"),
            resolvedEmailAddresses(
                addresses = listOf("First@Example.com"),
                pendingInput = " second@example.com; ",
            ),
        )
        assertEquals(
            listOf("alerts@example.com", "+41791234567"),
            resolvedAlertRecipients(
                recipients = listOf("Alerts@Example.com"),
                pendingInput = "+41 79 123 45 67",
            ),
        )
    }

    @Test
    fun pendingDuplicatesAreResolvedOnlyOnce() {
        assertEquals(
            listOf("alerts@example.com"),
            resolvedEmailAddresses(
                addresses = listOf("Alerts@Example.com"),
                pendingInput = "alerts@example.com",
            ),
        )
        assertEquals(
            listOf("+33612345678"),
            resolvedAlertRecipients(
                recipients = listOf("+33 6 12 34 56 78"),
                pendingInput = "+33612345678",
            ),
        )
    }
}
