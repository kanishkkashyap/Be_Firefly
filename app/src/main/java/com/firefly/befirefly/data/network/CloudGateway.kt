package com.firefly.befirefly.data.network

import kotlinx.coroutines.flow.Flow

interface CloudGateway {
    fun sendToCloud(packet: NetworkPacket)
    fun listenForMessages(userId: String): Flow<NetworkPacket>
}
