package com.glancemap.glancemapcompanionapp.transfer.datalayer

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import org.junit.Assert.assertEquals
import org.junit.Test

class WearableApiContractTest {
    @Test
    fun `companion module wearable api surface still matches transfer layer`() {
        assertEquals("com.google.android.gms.wearable.CapabilityClient", CapabilityClient::class.java.name)
        assertEquals(
            CapabilityClient.OnCapabilityChangedListener::class.java,
            CompileOnlyCapabilityListener::class.java.interfaces.single(),
        )
        assertEquals("com.google.android.gms.wearable.MessageClient", MessageClient::class.java.name)
        assertEquals("com.google.android.gms.wearable.ChannelClient", ChannelClient::class.java.name)
        assertEquals("com.google.android.gms.wearable.NodeClient", NodeClient::class.java.name)
    }

    @Suppress("unused")
    private fun compileOnlyWearableClientCalls(
        context: Context,
        capabilityListener: CapabilityClient.OnCapabilityChangedListener,
        messageListener: MessageClient.OnMessageReceivedListener,
        channel: ChannelClient.Channel,
    ) {
        val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
        val messageClient: MessageClient = Wearable.getMessageClient(context)
        val channelClient: ChannelClient = Wearable.getChannelClient(context)
        val nodeClient: NodeClient = Wearable.getNodeClient(context)
        val nodeId = "node"
        val path = "/path"
        val payload = ByteArray(0)

        capabilityClient.addListener(capabilityListener, DataLayerPaths.WEAR_CAPABILITY)
        capabilityClient.removeListener(capabilityListener)
        capabilityClient.getCapability(DataLayerPaths.WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        messageClient.addListener(messageListener)
        messageClient.removeListener(messageListener)
        messageClient.sendMessage(nodeId, path, payload)
        channelClient.openChannel(nodeId, path)
        channelClient.getOutputStream(channel)
        channelClient.close(channel)
        nodeClient.connectedNodes
        channel.path
    }

    private class CompileOnlyCapabilityListener : CapabilityClient.OnCapabilityChangedListener {
        override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) = Unit
    }
}
