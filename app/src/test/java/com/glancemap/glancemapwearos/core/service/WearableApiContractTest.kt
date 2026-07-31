package com.glancemap.glancemapwearos.core.service

import android.content.Context
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.junit.Assert.assertEquals
import org.junit.Test

class WearableApiContractTest {
    @Test
    fun `watch module wearable api surface still matches transfer layer`() {
        assertEquals(
            "com.google.android.gms.wearable.WearableListenerService",
            WearableListenerService::class.java.name,
        )
        assertEquals(WearableListenerService::class.java, CompileOnlyListener::class.java.superclass)
        assertEquals("com.google.android.gms.wearable.MessageClient", MessageClient::class.java.name)
        assertEquals("com.google.android.gms.wearable.ChannelClient", ChannelClient::class.java.name)
        assertEquals("com.google.android.gms.wearable.NodeClient", NodeClient::class.java.name)
    }

    @Suppress("unused")
    private fun compileOnlyWearableClientCalls(
        context: Context,
        channel: ChannelClient.Channel,
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) {
        val messageClient: MessageClient = Wearable.getMessageClient(context)
        val channelClient: ChannelClient = Wearable.getChannelClient(context)
        val nodeClient: NodeClient = Wearable.getNodeClient(context)

        messageClient.sendMessage(nodeId, path, payload)
        channelClient.getInputStream(channel)
        channelClient.close(channel)
        nodeClient.connectedNodes
        channel.path
    }

    private class CompileOnlyListener : WearableListenerService() {
        override fun onMessageReceived(messageEvent: MessageEvent) = Unit

        override fun onChannelOpened(channel: ChannelClient.Channel) = Unit

        override fun onPeerConnected(peer: Node) = Unit

        override fun onPeerDisconnected(peer: Node) = Unit
    }
}
