package com.smsforwarder.app.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Data Model Representing A Forwarded Sms Transaction Record.
 * Stores Full Timestamp, Sender Identification, Payload, And Delivery Status.
 */
data class ForwardLog(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("sender")
    val sender: String,

    @SerializedName("originalMessage")
    val originalMessage: String,

    @SerializedName("forwardedTo")
    val forwardedTo: String,

    @SerializedName("status")
    val status: String, // "Sent", "Failed", "Filtered"

    @SerializedName("errorMessage")
    val errorMessage: String? = null
)
