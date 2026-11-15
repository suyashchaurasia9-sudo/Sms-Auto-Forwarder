package com.smsforwarder.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smsforwarder.app.MainActivity
import com.smsforwarder.app.R
import com.smsforwarder.app.SmsForwarderApplication
import com.smsforwarder.app.data.AppPreferences
import com.smsforwarder.app.receiver.BootReceiver

/**
 * Unkillable Persistent Foreground Service Ensuring Continuous 24/7 Background Sms Forwarding.
 * Implements Task Removal Survival And System Reboot Auto-Revival Hooks.
 */
class SmsForwarderService : Service() {

    companion object {
        private const val Tag = "SmsForwarderService"
        private const val NotificationIdentifier = 2001
        const val ActionStop = "com.smsforwarder.app.ActionStopService"
        const val ActionRestart = "com.smsforwarder.app.ActionRestartService"

        /**
         * Starts Or Binds The Foreground Service Safe For All Supported Android Versions.
         */
        fun startService(context: Context) {
            val serviceIntent = Intent(context, SmsForwarderService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (exception: Exception) {
                Log.e(Tag, "Error Starting Foreground Service", exception)
            }
        }

        /**
         * Stops The Running Foreground Service And Removes Persistent Notification.
         */
        fun stopService(context: Context) {
            val serviceIntent = Intent(context, SmsForwarderService::class.java).apply {
                action = ActionStop
            }
            context.stopService(serviceIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(Tag, "Sms Forwarder Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            Log.d(Tag, "Stop Action Received. Shutting Down Foreground Service.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val preferences = AppPreferences(this)
        if (!preferences.isForwardingEnabled) {
            Log.d(Tag, "Forwarding Disabled In Preferences. Halting Service.")
            stopSelf()
            return START_NOT_STICKY
        }

        val persistentNotification = BuildForegroundNotification(preferences.targetPhoneNumber)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationIdentifier,
                    persistentNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationIdentifier, persistentNotification)
            }
        } catch (exception: Exception) {
            Log.e(Tag, "Error While Calling Start Foreground", exception)
        }

        // Start Sticky Tells Android To Recreate Service Automatically If Killed By Low Memory
        return START_STICKY
    }

    /**
     * Triggered When User Swipes The App Out Of Recent Tasks.
     * Schedules Immediate Alarm To Revive Background Forwarding Within One Second.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(Tag, "App Swiped From Recents. Scheduling Immediate Service Revival.")

        val preferences = AppPreferences(this)
        if (preferences.isForwardingEnabled) {
            ScheduleServiceRestart()
        }
    }

    /**
     * Triggered If The Service Ever Gets Destroyed Involuntarily By Operating System.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.w(Tag, "Sms Forwarder Service Destroyed. Enforcing Revival.")
        val preferences = AppPreferences(this)
        if (preferences.isForwardingEnabled) {
            ScheduleServiceRestart()
        }
    }

    /**
     * Employs System Alarm Manager To Wake Up And Restart Service After Briefest Delay.
     */
    private fun ScheduleServiceRestart() {
        try {
            val restartIntent = Intent(applicationContext, BootReceiver::class.java).apply {
                action = ActionRestart
            }
            val pendingBroadcast = PendingIntent.getBroadcast(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pendingBroadcast
            )
        } catch (exception: Exception) {
            Log.e(Tag, "Failed To Schedule Background Service Revival Alarm", exception)
        }
    }

    /**
     * Builds The Silent, Non-Intrusive Persistent Foreground Notification.
     */
    private fun BuildForegroundNotification(targetPhone: String): Notification {
        val launchAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val destinationDisplay = if (targetPhone.isNotBlank()) targetPhone else "Configured Phone Number"

        return NotificationCompat.Builder(this, SmsForwarderApplication.ChannelIdService)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle("Sms Forwarder: Active In Background")
            .setContentText("Auto Forwarding Incoming Sms Messages To: $destinationDisplay")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(launchPendingIntent)
            .build()
    }
}
