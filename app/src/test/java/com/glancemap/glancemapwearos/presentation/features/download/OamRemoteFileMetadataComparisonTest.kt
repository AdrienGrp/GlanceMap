package com.glancemap.glancemapwearos.presentation.features.download

import org.junit.Assert.assertEquals
import org.junit.Test

class OamRemoteFileMetadataComparisonTest {
    @Test
    fun differentEtagWithSameLastModifiedAndSizeStaysUpToDate() {
        val previous =
            metadata(
                entityTag = "\"first\"",
                lastModifiedMillis = 1_769_977_800_000L,
                contentLengthBytes = 12_108_242L,
            )
        val current =
            metadata(
                entityTag = "\"second\"",
                lastModifiedMillis = 1_769_977_800_000L,
                contentLengthBytes = 12_108_242L,
            )

        assertEquals(RemoteMetadataComparison.SAME, previous.compareWith(current))
    }

    @Test
    fun differentEtagWithoutStableIdentityStillMarksChanged() {
        val previous =
            metadata(
                entityTag = "\"first\"",
                lastModifiedMillis = 1_769_977_800_000L,
                contentLengthBytes = 12_108_242L,
            )
        val current =
            metadata(
                entityTag = "\"second\"",
                lastModifiedMillis = 1_769_977_900_000L,
                contentLengthBytes = 12_108_242L,
            )

        assertEquals(RemoteMetadataComparison.CHANGED, previous.compareWith(current))
    }

    @Test
    fun sameLastModifiedWithoutEtagStaysUpToDate() {
        val previous =
            metadata(
                entityTag = null,
                lastModifiedMillis = 1_769_977_800_000L,
                contentLengthBytes = null,
            )
        val current =
            metadata(
                entityTag = null,
                lastModifiedMillis = 1_769_977_800_000L,
                contentLengthBytes = null,
            )

        assertEquals(RemoteMetadataComparison.SAME, previous.compareWith(current))
    }

    private fun metadata(
        entityTag: String?,
        lastModifiedMillis: Long?,
        contentLengthBytes: Long?,
    ): OamRemoteFileMetadata =
        OamRemoteFileMetadata(
            url = "https://example.test/europe-madeira.map.zip",
            fileName = "europe-madeira.map.zip",
            entityTag = entityTag,
            lastModifiedMillis = lastModifiedMillis,
            contentLengthBytes = contentLengthBytes,
        )
}
