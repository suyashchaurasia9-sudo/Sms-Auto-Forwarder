package com.smsforwarder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.smsforwarder.app.data.AppPreferences
import com.smsforwarder.app.service.SmsForwarderService

/**
 * Main Application Class Responsible For System Initialization.
 * Sets Up Silent Notification Channels And Auto-Starts Background Service If Configured.
 */
class SmsForwarderApplication : Application() {

    companion object {
        // Unique Channel Identifier For Persistent Foreground Service Notification
        const val ChannelIdService = "ChannelSmsForwarderService"

        // Unique Channel Identifier For Operational System Alerts
        const val ChannelIdAlerts = "ChannelSmsForwarderAlerts"
    }

    override fun onCreate() {
        super.onCreate()

        // Create Silent System Notification Channels
        CreateNotificationChannels()

        // Auto-Revive Background Service If Forwarding Was Previously Enabled By User
        val preferences = AppPreferences(this)
        if (preferences.isForwardingEnabled && preferences.targetPhoneNumber.isNotBlank()) {
            SmsForwarderService.startService(this)
        }
    }

    /**
     * Initializes System Notification Channels For Android Oreo And Above.
     */
    private fun CreateNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low Priority Channel For Background Service To Prevent Unwanted Sound Alerts
            val serviceChannel = NotificationChannel(
                ChannelIdService,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_desc)
                setShowBadge(false)
            }

            // Normal Priority Channel For High Importance Delivery Notifications
            val alertsChannel = NotificationChannel(
                ChannelIdAlerts,
                "Forwarding Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications When Incoming Sms Messages Are Dispatched"
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }
}
