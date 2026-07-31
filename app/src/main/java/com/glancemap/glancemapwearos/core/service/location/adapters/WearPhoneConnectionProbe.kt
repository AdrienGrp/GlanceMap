package com.glancemap.glancemapwearos.core.service.location.adapters

import com.google.android.gms.wearable.NodeClient
import kotlinx.coroutines.tasks.await

internal class WearPhoneConnectionProbe(
    private val nodeClient: NodeClient,
) {
    suspend fun isPhoneConnected(): Boolean? =
        runCatching {
            nodeClient.connectedNodes.await().isNotEmpty()
        }.getOrNull()
}
