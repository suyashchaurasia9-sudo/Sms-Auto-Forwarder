package com.smsforwarder.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility Object Providing Permission Validation And Dynamic Query Methods.
 */
object PermissionHelper {

    /**
     * Retrieves An Array Of All Mandatory System Permissions Required By The Application.
     */
    fun getRequiredPermissions(): Array<String> {
        val permissionsList = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        // Include Post Notifications Permission For Android 13 Tiramisu And Newer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissionsList.toTypedArray()
    }

    /**
     * Checks Whether All Mandatory Permissions Have Been Granted By The User.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        for (permission in getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Specifically Verifies Core Sms Intercept And Send Capabilities.
     */
    fun hasSmsPermissions(context: Context): Boolean {
        val receiveGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val sendGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return receiveGranted && sendGranted
    }
}
