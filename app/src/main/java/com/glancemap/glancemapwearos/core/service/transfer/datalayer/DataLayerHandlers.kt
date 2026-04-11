package com.glancemap.glancemapwearos.core.service.transfer.datalayer

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.glancemap.glancemapwearos.core.service.DataLayerListenerService
import com.glancemap.glancemapwearos.core.service.transfer.notifications.NotificationHelper
import com.glancemap.glancemapwearos.core.service.transfer.runtime.TransferSessionState
import com.glancemap.glancemapwearos.core.service.transfer.storage.WatchFileOps
import kotlinx.coroutines.sync.Mutex

internal class DataLayerHandlers(
    private val service: DataLayerListenerService,
    private val notificationHelper: NotificationHelper,
    private val fileOps: WatchFileOps,
    private val transferMutex: Mutex,
    private val channelReceiver: ChannelClientStrategy,
    private val sessionState: TransferSessionState,
    private val sendStatus: suspend (sourceNodeId: String, transferId: String, phase: String, detail: String) -> Unit,
    private val sendAck: suspend (sourceNodeId: String, transferId: String, status: String, detail: String) -> Unit,
    private val sendMessage: suspend (sourceNodeId: String, path: String, payload: ByteArray) -> Unit
) {
    private val messageHandler = DataLayerMessageRequestHandler(
        service = service,
        notificationHelper = notificationHelper,
        fileOps = fileOps,
        transferMutex = transferMutex,
        sessionState = sessionState,
        sendStatus = sendStatus,
        sendAck = sendAck,
        sendMessage = sendMessage
    )

    private val channelOpenedHandler = DataLayerChannelOpenedHandler(
        service = service,
        notificationHelper = notificationHelper,
        fileOps = fileOps,
        transferMutex = transferMutex,
        channelReceiver = channelReceiver,
        sendAck = sendAck,
        popChannelChecksum = messageHandler::popChannelChecksum
    )

    fun handleMessage(messageEvent: MessageEvent) {
        messageHandler.handleMessage(messageEvent)
    }

    suspend fun handleChannelOpened(channel: ChannelClient.Channel) {
        channelOpenedHandler.handleChannelOpened(channel)
    }
}
