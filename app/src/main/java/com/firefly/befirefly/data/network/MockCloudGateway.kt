package com.firefly.befirefly.data.network

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MockCloudGateway : CloudGateway {

    private val cloudPackets = MutableSharedFlow<NetworkPacket>()

    override fun sendToCloud(packet: NetworkPacket) {
        Log.d("MockCloud", "Uploading packet to cloud: ${packet.id} for ${packet.receiverId}")
        // Simulate network delay or processing?
        // For now, we just "store" it or log it. 
        // In a real app, this would hit a REST API or Firebase.
        
        // Loopback for testing: If we upload a packet, maybe we want to simulate receiving it elsewhere?
        // But for now, we just log success.
    }

    override fun listenForMessages(userId: String): Flow<NetworkPacket> {
        Log.d("MockCloud", "Listening for cloud messages for user: $userId")
        return cloudPackets.asSharedFlow()
    }
    
    // Test helper to inject a message "from the cloud"
    suspend fun simulateIncomingMessage(packet: NetworkPacket) {
        cloudPackets.emit(packet)
    }
}
