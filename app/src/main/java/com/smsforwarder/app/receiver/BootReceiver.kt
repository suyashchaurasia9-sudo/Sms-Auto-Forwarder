package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsforwarder.app.data.AppPreferences
import com.smsforwarder.app.service.SmsForwarderService

/**
 * Broadcast Receiver Responding To System Boot Completion And Revival Alarms.
 * Guarantees That Sms Forwarding Resumes Immediately After Reboot Without Requiring App Opening.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val Tag = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val receivedAction = intent.action
        Log.d(Tag, "Boot Or Revival Event Received: $receivedAction")

        val preferences = AppPreferences(context)
        if (preferences.isForwardingEnabled && preferences.targetPhoneNumber.isNotBlank()) {
            Log.d(Tag, "Reviving Sms Forwarder Background Service In Response To: $receivedAction")
            SmsForwarderService.startService(context)
        }
    }
}
