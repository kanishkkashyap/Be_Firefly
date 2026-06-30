package com.firefly.befirefly.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.firefly.befirefly.R
import com.firefly.befirefly.data.network.NearbyConnectionsManager
import android.content.pm.ServiceInfo

class MeshService : Service() {

    private val binder = MeshBinder()
    
    // We keep a single instance of the manager here
    lateinit var nearbyManager: NearbyConnectionsManager
        private set

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    private lateinit var notificationHelper: com.firefly.befirefly.utils.NotificationHelper

    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize the manager
            nearbyManager = NearbyConnectionsManager(applicationContext)
            notificationHelper = com.firefly.befirefly.utils.NotificationHelper(applicationContext)
            
            // Wire up the offline message queue
            val database = com.firefly.befirefly.data.local.AppDatabase.getDatabase(applicationContext)
            nearbyManager.setDatabase(database.pendingMessageDao())
            
            // Listen for new endpoints
            nearbyManager.onEndpointConnected = { endpointId, endpointName ->
                notificationHelper.showUserConnectedNotification(endpointName, endpointId)
            }
            startForegroundService()
        } catch (e: Exception) {
            android.util.Log.e("MeshService", "Fatal error in onCreate", e)
            // Even if we crash here, we try to keep service alive? 
            // No, if init fails, we are broken. But logging it is key.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.stopAll()
    }

    private fun startForegroundService() {
        val channelId = "MeshServiceChannel"
        val channelName = "Mesh Networking"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Be Firefly Mesh Active")
            .setContentText("Maintaining mesh network connection...")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists or use android.R.drawable.ic_dialog_info
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent notification
            .build()

        // ID must be > 0
        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                
                // Only add LOCATION type if we actually have the permission, otherwise we CRASH
                val hasLocation = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasLocation) {
                    types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }

                startForeground(1, notification, types)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
