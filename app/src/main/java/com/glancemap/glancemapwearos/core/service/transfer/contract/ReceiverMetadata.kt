package com.glancemap.glancemapwearos.core.service.transfer.contract

data class ReceiverMetadata(
    val transferId: String,
    val fileName: String,
    val totalSize: Long,
    val sourceNodeId: String,
    val notificationId: Int,
    val checksumSha256: String? = null,
    val authToken: String? = null,
    // Wi-Fi fields
    val ip: String = "",
    val port: Int = -1,
)
