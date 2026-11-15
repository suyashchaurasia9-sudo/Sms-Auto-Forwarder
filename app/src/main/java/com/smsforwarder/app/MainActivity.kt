package com.smsforwarder.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.smsforwarder.app.data.AppPreferences
import com.smsforwarder.app.data.ForwardLog
import com.smsforwarder.app.databinding.ActivityMainBinding
import com.smsforwarder.app.service.SmsForwarderService
import com.smsforwarder.app.ui.ForwardLogAdapter
import com.smsforwarder.app.util.BatteryOptimizationHelper
import com.smsforwarder.app.util.PermissionHelper
import com.smsforwarder.app.util.SmsSender

/**
 * Main Activity Presenting Cyber Black And Neon Green Forwarder Cockpit Interface.
 * Manages Master Forwarding Switch, Destination Phone Input, Sim Selector, And Live Stream.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var preferences: AppPreferences
    private lateinit var logAdapter: ForwardLogAdapter

    private var activeSimSubscriptions = listOf<SubscriptionInfo>()

    // Broadcast Receiver Dynamically Refreshing Displayed Log History Upon Forwarding Event
    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            LoadHistoryLogs()
        }
    }

    // Modern Activity Result Contract For Requesting All Mandatory Telephony Permissions
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val smsPermitted = PermissionHelper.hasSmsPermissions(this)
        UpdatePermissionIndicators()
        LoadSimCards()

        if (smsPermitted) {
            Toast.makeText(this, "Sms Permissions Granted Successfully!", Toast.LENGTH_SHORT).show()
            AutoPromptBatteryOptimization()
            if (preferences.isForwardingEnabled) {
                SmsForwarderService.startService(this)
            }
        } else {
            DisplayPermissionSettingsDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        preferences = AppPreferences(this)

        SetupUserInterface()
        SetupActionListeners()
        LoadSavedPreferences()
        SetupHistoryRecyclerView()

        // Automatically Trigger Battery Saver Exemption On Initial Launch If Enabled
        if (preferences.isForwardingEnabled || preferences.targetPhoneNumber.isNotBlank()) {
            AutoPromptBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()
        UpdatePermissionIndicators()
        LoadSimCards()
        LoadHistoryLogs()
        UpdateHeroStatusCard()

        // Register Dynamic Broadcast Receiver For Instant Log Synchronization
        val broadcastFilter = IntentFilter(AppPreferences.ActionLogsUpdated)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logUpdateReceiver, broadcastFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logUpdateReceiver, broadcastFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logUpdateReceiver)
        } catch (ignored: Exception) {
            // Receiver Unregister Guard
        }
    }

    private fun SetupUserInterface() {
        // Set Active Radio Button Based On Saved Filter Rule
        if (preferences.filterRule == AppPreferences.FilterOtpOnly) {
            viewBinding.rbForwardOtpOnly.isChecked = true
        } else {
            viewBinding.rbForwardAll.isChecked = true
        }

        UpdateHeroStatusCard()
    }

    private fun SetupActionListeners() {
        // Master Forwarding Activation Switch Listener
        viewBinding.switchForwarding.setOnCheckedChangeListener { _, isChecked ->
            val enteredTargetNumber = viewBinding.etTargetPhone.text.toString().trim()

            if (isChecked) {
                if (enteredTargetNumber.length < 7) {
                    viewBinding.switchForwarding.isChecked = false
                    viewBinding.tilTargetPhone.error = "Please Enter A Valid Destination Phone Number"
                    Toast.makeText(this, "Please Enter A Valid Phone Number First", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }

                viewBinding.tilTargetPhone.error = null
                preferences.targetPhoneNumber = enteredTargetNumber
                preferences.isForwardingEnabled = true

                // Check And Request Missing Telephony Permissions
                if (!PermissionHelper.hasSmsPermissions(this)) {
                    permissionRequestLauncher.launch(PermissionHelper.getRequiredPermissions())
                }

                // Automatically Execute Battery Optimization Exemption Itself
                AutoPromptBatteryOptimization()

                SmsForwarderService.startService(this)
                UpdateHeroStatusCard()
                DisplaySnackbar("Background Forwarding Engine Activated")
            } else {
                preferences.isForwardingEnabled = false
                SmsForwarderService.stopService(this)
                UpdateHeroStatusCard()
                DisplaySnackbar("Background Forwarding Suspended")
            }
        }

        // Save Configuration Button Listener
        viewBinding.btnSaveSettings.setOnClickListener { clickedButton ->
            AnimateButtonPress(clickedButton)
            val enteredPhoneNumber = viewBinding.etTargetPhone.text.toString().trim()
            if (enteredPhoneNumber.length < 7) {
                viewBinding.tilTargetPhone.error = "Please Enter A Valid Phone Number"
                return@setOnClickListener
            }

            viewBinding.tilTargetPhone.error = null
            preferences.targetPhoneNumber = enteredPhoneNumber

            // Persist Sim Selection Preference
            val selectedSimIndex = viewBinding.spinnerSim.selectedItemPosition
            if (selectedSimIndex > 0 && selectedSimIndex - 1 < activeSimSubscriptions.size) {
                val subscription = activeSimSubscriptions[selectedSimIndex - 1]
                preferences.selectedSimId = subscription.subscriptionId
            } else {
                preferences.selectedSimId = -1 // Default System Sim
            }

            // Persist Selected Filtering Mode
            preferences.filterRule = if (viewBinding.rbForwardOtpOnly.isChecked) {
                AppPreferences.FilterOtpOnly
            } else {
                AppPreferences.FilterAll
            }

            // Automatically Request Battery Exemption If Needed
            AutoPromptBatteryOptimization()

            if (preferences.isForwardingEnabled) {
                SmsForwarderService.startService(this)
            }

            UpdateHeroStatusCard()
            Toast.makeText(this, "Configuration Saved Successfully!", Toast.LENGTH_SHORT).show()
        }

        // Send Test Sms Button Listener
        viewBinding.btnSendTest.setOnClickListener { clickedButton ->
            AnimateButtonPress(clickedButton)
            ExecuteTestSmsDispatch()
        }

        // Grant System Permissions Button Listener
        viewBinding.btnGrantPermissions.setOnClickListener {
            RequestSmsPermissionsWithFallback()
        }

        // Whole Sms Permission Row Clickable
        viewBinding.rowPermSms.setOnClickListener {
            RequestSmsPermissionsWithFallback()
        }

        // Request Direct Battery Saver Exemption
        viewBinding.btnExemptBattery.setOnClickListener {
            AutoPromptBatteryOptimization()
        }

        // Whole Battery Row Clickable
        viewBinding.rowPermBattery.setOnClickListener {
            AutoPromptBatteryOptimization()
        }

        // Open Manufacturer Background Autostart Settings
        viewBinding.btnOemSettings.setOnClickListener {
            DisplayOemGuidanceDialog()
        }

        // Whole Oem Row Clickable
        viewBinding.rowOemAutostart.setOnClickListener {
            DisplayOemGuidanceDialog()
        }

        // Forwarding Filter Radio Group Listener
        viewBinding.rgFilterRule.setOnCheckedChangeListener { _, checkedId ->
            preferences.filterRule = if (checkedId == R.id.rbForwardOtpOnly) {
                AppPreferences.FilterOtpOnly
            } else {
                AppPreferences.FilterAll
            }
        }

        // Clear Stored Forwarding Log History
        viewBinding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Log History")
                .setMessage("Are You Sure You Want To Clear All Forwarded Sms Logs?")
                .setPositiveButton("Clear Log") { _, _ ->
                    preferences.clearLogs()
                    LoadHistoryLogs()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun LoadSavedPreferences() {
        viewBinding.etTargetPhone.setText(preferences.targetPhoneNumber)
        viewBinding.switchForwarding.isChecked = preferences.isForwardingEnabled
        UpdateHeroStatusCard()
    }

    private fun UpdateHeroStatusCard() {
        val forwardingActive = preferences.isForwardingEnabled
        val destinationPhone = preferences.targetPhoneNumber

        if (forwardingActive) {
            // Activate Pulsing Radar Beacon Animation
            val radarPulse = AnimationUtils.loadAnimation(this, R.anim.pulse_beacon)
            viewBinding.beaconGlow.visibility = View.VISIBLE
            viewBinding.beaconGlow.startAnimation(radarPulse)

            viewBinding.beaconCore.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.neon_green)
            )

            viewBinding.tvHeroStatusTitle.text = "Running In Background"
            viewBinding.tvHeroStatusTitle.setTextColor(
                ContextCompat.getColor(this, R.color.neon_green)
            )
            viewBinding.tvHeroStatusSub.text = "Forwarding Active 24/7 Until Manually Switched Off"

            val formattedDestination = if (destinationPhone.isNotBlank()) destinationPhone else "Not Configured"
            viewBinding.tvActiveRoute.text = "Mother Phone  ➔  $formattedDestination"
            viewBinding.tvActiveRoute.setTextColor(
                ContextCompat.getColor(this, R.color.neon_green)
            )
        } else {
            // Halt Radar Beacon Animation
            viewBinding.beaconGlow.clearAnimation()
            viewBinding.beaconGlow.visibility = View.INVISIBLE

            viewBinding.beaconCore.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_inactive)
            )

            viewBinding.tvHeroStatusTitle.text = "Standby Mode"
            viewBinding.tvHeroStatusTitle.setTextColor(
                ContextCompat.getColor(this, R.color.status_inactive)
            )
            viewBinding.tvHeroStatusSub.text = "Toggle Switch To Begin Forwarding"

            val formattedDestination = if (destinationPhone.isNotBlank()) destinationPhone else "Not Configured"
            viewBinding.tvActiveRoute.text = "Mother Phone  ➔  $formattedDestination (Inactive)"
            viewBinding.tvActiveRoute.setTextColor(
                ContextCompat.getColor(this, R.color.text_muted)
            )
        }
    }

    private fun UpdatePermissionIndicators() {
        val smsPermitted = PermissionHelper.hasSmsPermissions(this)
        if (smsPermitted) {
            viewBinding.ivPermSmsStatus.setImageResource(R.drawable.ic_check_circle)
            viewBinding.ivPermSmsStatus.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.neon_green)
            )
            viewBinding.btnGrantPermissions.visibility = View.GONE
        } else {
            viewBinding.ivPermSmsStatus.setImageResource(R.drawable.ic_warning)
            viewBinding.ivPermSmsStatus.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_error)
            )
            viewBinding.btnGrantPermissions.visibility = View.VISIBLE
        }

        val batteryExempted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        if (batteryExempted) {
            viewBinding.ivPermBatteryStatus.setImageResource(R.drawable.ic_check_circle)
            viewBinding.ivPermBatteryStatus.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.neon_green)
            )
            viewBinding.btnExemptBattery.visibility = View.GONE
        } else {
            viewBinding.ivPermBatteryStatus.setImageResource(R.drawable.ic_warning)
            viewBinding.ivPermBatteryStatus.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_warning)
            )
            viewBinding.btnExemptBattery.visibility = View.VISIBLE
        }
    }

    /**
     * Automatically Requests Direct Exemption From Battery Optimization If Not Granted.
     */
    private fun AutoPromptBatteryOptimization() {
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun LoadSimCards() {
        val simOptionsList = mutableListOf<String>()
        simOptionsList.add("Default System Sim")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            activeSimSubscriptions = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()

            var preferredSelectionIndex = 0
            activeSimSubscriptions.forEachIndexed { slotIndex, info ->
                val networkCarrier = info.displayName ?: "Sim ${slotIndex + 1}"
                val hardwareSlot = info.simSlotIndex + 1
                simOptionsList.add("Sim $hardwareSlot • $networkCarrier")

                if (info.subscriptionId == preferences.selectedSimId) {
                    preferredSelectionIndex = slotIndex + 1
                }
            }

            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simOptionsList)
            viewBinding.spinnerSim.adapter = spinnerAdapter
            viewBinding.spinnerSim.setSelection(preferredSelectionIndex)
        } else {
            val defaultAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simOptionsList)
            viewBinding.spinnerSim.adapter = defaultAdapter
        }
    }

    private fun ExecuteTestSmsDispatch() {
        val destinationPhone = viewBinding.etTargetPhone.text.toString().trim()
        if (destinationPhone.length < 7) {
            viewBinding.tilTargetPhone.error = "Please Enter A Valid Phone Number"
            return
        }

        if (!PermissionHelper.hasSmsPermissions(this)) {
            Toast.makeText(this, "Please Grant Sms Permissions First", Toast.LENGTH_SHORT).show()
            permissionRequestLauncher.launch(PermissionHelper.getRequiredPermissions())
            return
        }

        val testMessagePayload = "[Sms Forwarder Test]\nDirect Sim Forwarding Is Active And Operational On This Device."
        Toast.makeText(this, "Dispatching Test Sms To $destinationPhone...", Toast.LENGTH_SHORT).show()

        val dispatchResult = SmsSender.sendSms(
            context = this,
            destinationAddress = destinationPhone,
            messageText = testMessagePayload,
            subscriptionId = preferences.selectedSimId
        )

        if (dispatchResult.isSuccess) {
            Toast.makeText(this, "Test Sms Successfully Dispatched Via Sim!", Toast.LENGTH_LONG).show()
            preferences.addLog(
                ForwardLog(
                    sender = "Test Dispatch",
                    originalMessage = testMessagePayload,
                    forwardedTo = destinationPhone,
                    status = "Sent"
                )
            )
            LoadHistoryLogs()
        } else {
            val errorMessage = dispatchResult.exceptionOrNull()?.localizedMessage ?: "Unknown Cellular Failure"
            Toast.makeText(this, "Failed: $errorMessage", Toast.LENGTH_LONG).show()
            preferences.addLog(
                ForwardLog(
                    sender = "Test Dispatch",
                    originalMessage = testMessagePayload,
                    forwardedTo = destinationPhone,
                    status = "Failed",
                    errorMessage = errorMessage
                )
            )
            LoadHistoryLogs()
        }
    }

    private fun SetupHistoryRecyclerView() {
        logAdapter = ForwardLogAdapter()
        viewBinding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
        LoadHistoryLogs()
    }

    private fun LoadHistoryLogs() {
        val persistedLogs = preferences.getLogs()
        if (persistedLogs.isEmpty()) {
            viewBinding.tvEmptyHistory.visibility = View.VISIBLE
            viewBinding.rvHistory.visibility = View.GONE
            viewBinding.tvHistoryHeader.text = "Live Forwarding Log (0)"
        } else {
            viewBinding.tvEmptyHistory.visibility = View.GONE
            viewBinding.rvHistory.visibility = View.VISIBLE
            viewBinding.tvHistoryHeader.text = "Live Forwarding Log (${persistedLogs.size})"
            logAdapter.submitList(persistedLogs)
        }
    }

    private fun AnimateButtonPress(targetButton: View) {
        targetButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(80)
            .withEndAction {
                targetButton.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(80)
                    .start()
            }
            .start()
    }

    private fun DisplaySnackbar(messageText: String) {
        Snackbar.make(viewBinding.root, messageText, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.bg_card_elevated))
            .setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            .show()
    }

    private fun RequestSmsPermissionsWithFallback() {
        if (PermissionHelper.hasSmsPermissions(this)) {
            Toast.makeText(this, "Sms Permissions Are Already Granted!", Toast.LENGTH_SHORT).show()
            UpdatePermissionIndicators()
            return
        }

        permissionRequestLauncher.launch(PermissionHelper.getRequiredPermissions())
    }

    private fun DisplayPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Grant Sms Permissions")
            .setMessage(
                "To Copy Incoming Messages And Forward Them To Your Phone, Android Requires Sms Permission.\n\n" +
                "Please Tap 'Open Settings', Select 'Permissions', And Allow 'Sms'."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(settingsIntent)
                } catch (exception: Exception) {
                    Toast.makeText(this, "Unable To Open Settings Automatically", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun DisplayOemGuidanceDialog() {
        val phoneBrand = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
        }
        AlertDialog.Builder(this)
            .setTitle("$phoneBrand Background Lock")
            .setMessage(
                "To Ensure 100% Uninterrupted Background Sms Forwarding On $phoneBrand:\n\n" +
                "1. Enable 'Auto-start' / 'Allow Background Activity' For Sms Forwarder In System Settings.\n" +
                "2. Open The Phone Multitasking Screen And Lock The Sms Forwarder Application."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                BatteryOptimizationHelper.openOemAutoStartSettings(this)
            }
            .setNegativeButton("Done", null)
            .show()
    }
}
