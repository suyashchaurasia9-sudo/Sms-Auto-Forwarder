package com.smsforwarder.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsforwarder.app.R
import com.smsforwarder.app.data.ForwardLog
import com.smsforwarder.app.databinding.ItemForwardLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recycler View Adapter Rendering The Live Feed Of Forwarded Sms Records.
 */
class ForwardLogAdapter : ListAdapter<ForwardLog, ForwardLogAdapter.LogViewHolder>(DiffCallback) {

    private val timestampFormatter = SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val itemBinding = ItemForwardLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(private val itemBinding: ItemForwardLogBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(logRecord: ForwardLog) {
            val componentContext = itemBinding.root.context

            itemBinding.tvSender.text = logRecord.sender
            itemBinding.tvTimestamp.text = timestampFormatter.format(Date(logRecord.timestamp))
            itemBinding.tvMessage.text = logRecord.originalMessage
            itemBinding.tvTarget.text = "Dispatched To: ${logRecord.forwardedTo}"

            when (logRecord.status) {
                "Sent" -> {
                    val activeColor = ContextCompat.getColor(componentContext, R.color.neon_green)
                    val glowColor = ContextCompat.getColor(componentContext, R.color.neon_green_dim)
                    itemBinding.viewStatusStrip.setBackgroundColor(activeColor)
                    itemBinding.tvStatus.text = "Sent"
                    itemBinding.tvStatus.setTextColor(activeColor)
                    itemBinding.tvStatus.backgroundTintList = ColorStateList.valueOf(glowColor)
                }
                "Failed" -> {
                    val errorColor = ContextCompat.getColor(componentContext, R.color.status_error)
                    val glowColor = ContextCompat.getColor(componentContext, R.color.status_error_glow)
                    itemBinding.viewStatusStrip.setBackgroundColor(errorColor)
                    itemBinding.tvStatus.text = "Failed"
                    itemBinding.tvStatus.setTextColor(errorColor)
                    itemBinding.tvStatus.backgroundTintList = ColorStateList.valueOf(glowColor)
                    if (!logRecord.errorMessage.isNullOrBlank()) {
                        itemBinding.tvTarget.text = "Error: ${logRecord.errorMessage}"
                    }
                }
                "Filtered" -> {
                    val warningColor = ContextCompat.getColor(componentContext, R.color.status_warning)
                    itemBinding.viewStatusStrip.setBackgroundColor(warningColor)
                    itemBinding.tvStatus.text = "Filtered"
                    itemBinding.tvStatus.setTextColor(warningColor)
                    itemBinding.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#26372000"))
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ForwardLog>() {
            override fun areItemsTheSame(oldItem: ForwardLog, newItem: ForwardLog): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ForwardLog, newItem: ForwardLog): Boolean {
                return oldItem == newItem
            }
        }
    }
}
