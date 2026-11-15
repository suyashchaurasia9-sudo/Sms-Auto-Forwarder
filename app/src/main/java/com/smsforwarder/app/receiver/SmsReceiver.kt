package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.smsforwarder.app.data.AppPreferences
import com.smsforwarder.app.data.ForwardLog
import com.smsforwarder.app.util.SmsSender

/**
 * High Priority Broadcast Receiver Listening For Telephony Incoming Sms Events.
 * Stitches Multipart Text Messages, Applies Keyword Filtering, And Triggers Cellular Forwarding.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val Tag = "SmsReceiver"

        // Cache Preventing Duplicate Forwarding When Broadcast Is Delivered Concurrently
        private val ProcessedSignatures = LinkedHashMap<String, Long>()

        // Keywords Matching Financial Transactions, One Time Passwords, And Bank Notifications
        private val OtpKeywords = listOf(
            "otp", "code", "bank", "credit", "debit", "acct", "account",
            "txn", "transaction", "inr", "rs.", "usd", "vpa", "upi",
            "password", "pin", "verify", "verification", "auth", "login",
            "card", "citi", "hdfc", "sbi", "icici", "axis", "pnb", "bob"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val preferences = AppPreferences(context)
        if (!preferences.isForwardingEnabled) {
            Log.d(Tag, "Forwarding Engine Is In Standby Mode. Skipping Processing.")
            return
        }

        val targetPhone = preferences.targetPhoneNumber.trim()
        if (targetPhone.isBlank()) {
            Log.w(Tag, "Target Phone Number Is Not Configured. Skipping Processing.")
            return
        }

        val rawMessages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        } catch (exception: Exception) {
            Log.e(Tag, "Failed To Parse Incoming Sms Messages From Intent", exception)
            return
        }

        if (rawMessages.isEmpty()) {
            return
        }

        // Acquire Temporary Partial Wake Lock To Prevent Cpu From Sleeping During Forwarding
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmsForwarder:ProcessingWakeLock"
        )
        wakeLock?.acquire(15000L) // 15 Seconds Maximum Safety Timeout

        try {
            // Group Incoming Multipart Segments By Originating Sender
            val groupedMessages = mutableMapOf<String, StringBuilder>()

            for (sms in rawMessages) {
                val originatingSender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown Sender"
                val messageFragment = sms.displayMessageBody ?: sms.messageBody ?: ""

                val messageBuilder = groupedMessages.getOrPut(originatingSender) { StringBuilder() }
                messageBuilder.append(messageFragment)
            }

            for ((sender, fullBodyBuilder) in groupedMessages) {
                val completeMessageBody = fullBodyBuilder.toString()
                ProcessIncomingMessage(context, preferences, sender, completeMessageBody, targetPhone)
            }
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    /**
     * Evaluates Filtering Rules, Constructs Outgoing Payload, And Dispatches Sms Via Local Sim.
     */
    private fun ProcessIncomingMessage(
        context: Context,
        preferences: AppPreferences,
        sender: String,
        body: String,
        targetPhone: String
    ) {
        Log.d(Tag, "Processing Incoming Message Received From: $sender")

        // 0. De-Duplication Guard
        val messageSignature = "$sender:$body"
        synchronized(ProcessedSignatures) {
            val currentTime = System.currentTimeMillis()
            val iterator = ProcessedSignatures.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (currentTime - entry.value > 60000L) {
                    iterator.remove()
                }
            }
            if (ProcessedSignatures.containsKey(messageSignature)) {
                Log.d(Tag, "Duplicate Broadcast Detected. Skipping Redundant Forward.")
                return
            }
            ProcessedSignatures[messageSignature] = currentTime
        }

        // 1. Loop Protection: Avoid Forwarding Messages Sent By Target Device Itself
        val sanitizedSender = sender.replace("+", "").replace(" ", "").replace("-", "")
        val sanitizedTarget = targetPhone.replace("+", "").replace(" ", "").replace("-", "")
        if (sanitizedSender.isNotBlank() && sanitizedSender == sanitizedTarget) {
            Log.w(Tag, "Sender Matches Target Destination. Dropped Message To Prevent Infinite Forwarding Loop.")
            return
        }

        // 2. Evaluate Rule Filter When Set To Otp Only
        if (preferences.filterRule == AppPreferences.FilterOtpOnly) {
            val lowercaseContent = (sender + " " + body).lowercase()
            val matchesCriteria = OtpKeywords.any { keyword -> lowercaseContent.contains(keyword) }

            if (!matchesCriteria) {
                Log.d(Tag, "Message Filtered Out As It Does Not Match Otp Or Banking Keywords.")
                preferences.addLog(
                    ForwardLog(
                        sender = sender,
                        originalMessage = body,
                        forwardedTo = targetPhone,
                        status = "Filtered",
                        errorMessage = "Filtered: Message Does Not Match Otp Or Banking Keywords"
                    )
                )
                return
            }
        }

        // 3. Construct Formatted Forwarding Payload
        val prefix = preferences.prefixTemplate.replace("{sender}", sender)
        val textToForward = "$prefix$body"

        // 4. Dispatch Forwarded Sms Message Via Hardware Sim
        val dispatchResult = SmsSender.sendSms(
            context = context,
            destinationAddress = targetPhone,
            messageText = textToForward,
            subscriptionId = preferences.selectedSimId
        )

        // 5. Record Dispatch Outcome To Persistent Transaction History
        if (dispatchResult.isSuccess) {
            Log.i(Tag, "Successfully Forwarded Sms From $sender To $targetPhone")
            preferences.addLog(
                ForwardLog(
                    sender = sender,
                    originalMessage = body,
                    forwardedTo = targetPhone,
                    status = "Sent"
                )
            )
        } else {
            val failureReason = dispatchResult.exceptionOrNull()?.localizedMessage ?: "Unknown Cellular Dispatch Error"
            Log.e(Tag, "Failed Forwarding Sms From $sender: $failureReason")
            preferences.addLog(
                ForwardLog(
                    sender = sender,
                    originalMessage = body,
                    forwardedTo = targetPhone,
                    status = "Failed",
                    errorMessage = failureReason
                )
            )
        }
    }
}
