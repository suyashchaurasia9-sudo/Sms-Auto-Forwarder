package com.smsforwarder.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Encapsulates Local Shared Preferences For Persistent Forwarder Configuration.
 * Manages Forwarding State, Destination Phone Number, Sim Preference, And Log Storage.
 */
class AppPreferences(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PreferenceName, Context.MODE_PRIVATE)
    private val gsonParser = Gson()

    companion object {
        private const val PreferenceName = "SmsForwarderPreferences"
        private const val KeyForwardingEnabled = "KeyForwardingEnabled"
        private const val KeyTargetPhone = "KeyTargetPhone"
        private const val KeySelectedSim = "KeySelectedSim"
        private const val KeyFilterRule = "KeyFilterRule"
        private const val KeyPrefixTemplate = "KeyPrefixTemplate"
        private const val KeyLogsJson = "KeyLogsJson"

        // Filter Constants
        const val FilterAll = 0
        const val FilterOtpOnly = 1

        // Broadcast Action For Synchronizing Log Updates
        const val ActionLogsUpdated = "com.smsforwarder.app.ActionLogsUpdated"
        private const val MaximumLogsRetained = 100
    }

    // Indicates Whether Background Sms Forwarding Is Actively Enabled
    var isForwardingEnabled: Boolean
        get() = preferences.getBoolean(KeyForwardingEnabled, false)
        set(value) = preferences.edit().putBoolean(KeyForwardingEnabled, value).apply()

    // Configured Destination Phone Number That Receives Forwarded Sms
    var targetPhoneNumber: String
        get() = preferences.getString(KeyTargetPhone, "") ?: ""
        set(value) = preferences.edit().putString(KeyTargetPhone, value.trim()).apply()

    // Hardware Sim Subscription Identifier (-1 Corresponds To System Default Sim)
    var selectedSimId: Int
        get() = preferences.getInt(KeySelectedSim, -1)
        set(value) = preferences.edit().putInt(KeySelectedSim, value).apply()

    // Active Rule Filter (All Messages Versus Otp And Banking Only)
    var filterRule: Int
        get() = preferences.getInt(KeyFilterRule, FilterAll)
        set(value) = preferences.edit().putInt(KeyFilterRule, value).apply()

    // Prefix Format Attached To Forwarded Sms Messages
    var prefixTemplate: String
        get() = preferences.getString(KeyPrefixTemplate, "[Fwd From {sender}]:\n") ?: "[Fwd From {sender}]:\n"
        set(value) = preferences.edit().putString(KeyPrefixTemplate, value).apply()

    /**
     * Appends A New Forwarding Transaction Record To Persistent Storage.
     * Enforces Maximum Log Cap And Broadcasts Refresh Event To Active Activities.
     */
    @Synchronized
    fun addLog(log: ForwardLog) {
        val existingLogs = getLogs().toMutableList()
        existingLogs.add(0, log) // Prepend Most Recent Log Entry

        if (existingLogs.size > MaximumLogsRetained) {
            existingLogs.subList(MaximumLogsRetained, existingLogs.size).clear()
        }

        val serializedJson = gsonParser.toJson(existingLogs)
        preferences.edit().putString(KeyLogsJson, serializedJson).apply()

        // Notify Open Activities To Reload Displayed History Instantly
        val updateBroadcastIntent = Intent(ActionLogsUpdated)
        context.sendBroadcast(updateBroadcastIntent)
    }

    /**
     * Retrieves All Persisted Forwarding Transaction Records.
     */
    @Synchronized
    fun getLogs(): List<ForwardLog> {
        val serializedJson = preferences.getString(KeyLogsJson, null) ?: return emptyList()
        return try {
            val listType = object : TypeToken<List<ForwardLog>>() {}.type
            gsonParser.fromJson(serializedJson, listType) ?: emptyList()
        } catch (exception: Exception) {
            emptyList()
        }
    }

    /**
     * Clears All Stored Forwarding Records.
     */
    @Synchronized
    fun clearLogs() {
        preferences.edit().remove(KeyLogsJson).apply()
        val updateBroadcastIntent = Intent(ActionLogsUpdated)
        context.sendBroadcast(updateBroadcastIntent)
    }
}
