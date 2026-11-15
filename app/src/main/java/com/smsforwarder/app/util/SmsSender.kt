package com.smsforwarder.app.util

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * Handles Outgoing Cellular Sms Dispatching Via System Sms Manager.
 * Fully Supports Long Multipart Sms Splitting And Multi-Sim Subscription Routing.
 */
object SmsSender {

    private const val Tag = "SmsSender"

    /**
     * Dispatches Cellular Sms Message To Specified Target Phone Number.
     * Manages Multipart Splitting If Message Exceeds Standard Character Limits.
     */
    fun sendSms(
        context: Context,
        destinationAddress: String,
        messageText: String,
        subscriptionId: Int = -1
    ): Result<Unit> {
        return try {
            val sanitizedNumber = destinationAddress.trim().replace(" ", "").replace("-", "")
            if (sanitizedNumber.isBlank()) {
                return Result.failure(IllegalArgumentException("Destination Phone Number Cannot Be Empty."))
            }

            // Resolve Appropriate System Sms Manager Instance
            val smsManager: SmsManager = ResolveSmsManager(context, subscriptionId)

            // Split Long Text Messages Into Multiple Parts To Prevent Truncation
            val messageParts = smsManager.divideMessage(messageText)

            if (messageParts.size > 1) {
                Log.d(Tag, "Dispatching Multipart Sms (${messageParts.size} Parts) To $sanitizedNumber")
                smsManager.sendMultipartTextMessage(
                    sanitizedNumber,
                    null,
                    messageParts,
                    null,
                    null
                )
            } else {
                Log.d(Tag, "Dispatching Single Sms To $sanitizedNumber")
                smsManager.sendTextMessage(
                    sanitizedNumber,
                    null,
                    messageText,
                    null,
                    null
                )
            }

            Result.success(Unit)
        } catch (exception: Exception) {
            Log.e(Tag, "Failed To Dispatch Outgoing Sms To $destinationAddress", exception)
            Result.failure(exception)
        }
    }

    /**
     * Resolves Hardware-Specific Or Default Sms Manager Based On Android Platform Version.
     */
    @Suppress("DEPRECATION")
    private fun ResolveSmsManager(context: Context, subscriptionId: Int): SmsManager {
        val defaultManager = SmsManager.getDefault()
        return try {
            val baseSmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: defaultManager
            } else {
                defaultManager
            }

            if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    baseSmsManager.createForSubscriptionId(subscriptionId)
                } else {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }
            } else {
                baseSmsManager
            }
        } catch (exception: Exception) {
            Log.w(Tag, "Failed Resolving Multi-Sim Manager. Falling Back To Default Sms Manager.", exception)
            defaultManager
        }
    }
}
