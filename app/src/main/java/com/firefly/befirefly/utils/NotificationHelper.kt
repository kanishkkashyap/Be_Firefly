package com.firefly.befirefly.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.firefly.befirefly.MainActivity
import com.firefly.befirefly.R
import android.graphics.BitmapFactory

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_CONNECTION = "channel_connection"
        const val CHANNEL_NAME_CONNECTION = "Connections"
        const val CHANNEL_ID_MESSAGE = "channel_message"
        const val CHANNEL_NAME_MESSAGE = "Messages"
        
        const val NOTIFICATION_ID_CONNECTION = 1001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val connectionChannel = NotificationChannel(
                CHANNEL_ID_CONNECTION,
                CHANNEL_NAME_CONNECTION,
                NotificationManager.IMPORTANCE_HIGH // Pop-up
            ).apply {
                description = "Notifications when a user connects via mesh or internet"
            }
            
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGE,
                CHANNEL_NAME_MESSAGE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages"
            }

            notificationManager.createNotificationChannel(connectionChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    fun showUserConnectedNotification(username: String, userId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Potentially pass userId to open specific chat
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONNECTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Use App Icon
            .setContentTitle("User Online")
            .setContentText("$username is now connected nearby!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(userId.hashCode(), builder.build())
    }
    
    fun showMessageNotification(senderName: String, messageValues: String, conversationId: String) {
         val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("conversationId", conversationId)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, conversationId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGE)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(messageValues)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        notificationManager.notify(conversationId.hashCode(), builder.build())
    }
}
