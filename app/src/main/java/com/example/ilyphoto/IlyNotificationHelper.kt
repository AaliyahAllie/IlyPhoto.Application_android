package com.example.ilyphoto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ilyphoto.data.Booking

object IlyNotificationHelper {

    private const val CHANNEL_ID = "ily_photo_alerts"
    private const val CHANNEL_NAME = "Ily Photo Studio Alerts"
    private const val CHANNEL_DESC =
        "Notifications for booking confirmations, rescheduling, status updates, and gallery downloads"
    private const val PREFS_NAME = "ily_photo_prefs"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isBookingNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("notif_booking", true)
    }

    private fun isGalleryNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("notif_gallery", true)
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun notifyBookingCreated(
        context: Context,
        serviceName: String,
        eventDate: String,
        invoiceNumber: String
    ) {
        if (!isBookingNotificationsEnabled(context)) return
        val title = "Booking Submitted: $serviceName"
        val msg =
            "Your $serviceName session on $eventDate has been saved to Azure Cloud (Invoice $invoiceNumber)."
        showNotification(context, title, msg, 104)
    }

    fun notifyBookingRescheduled(
        context: Context,
        serviceName: String,
        eventDate: String,
        eventTime: String,
        eventLocation: String
    ) {
        if (!isBookingNotificationsEnabled(context)) return
        val title = "Booking Rescheduled: $serviceName"
        val msg =
            "Updated to $eventDate at $eventTime ($eventLocation). All changes synced to Azure Cloud."
        showNotification(context, title, msg, 105)
    }

    fun notifyBookingStatus(context: Context, serviceName: String, status: String) {
        if (!isBookingNotificationsEnabled(context)) return
        val title = "Booking Update: $serviceName"
        val msg = when (status.lowercase()) {
            "approved", "confirmed" ->
                "Great news! Your booking for $serviceName has been APPROVED by the photographer."
            "rescheduled" ->
                "Your booking for $serviceName has been rescheduled and synced with the studio."
            "completed" ->
                "Your photo session for $serviceName is marked as completed! Photos are now in post-processing."
            "cancelled" ->
                "Your booking for $serviceName has been cancelled."
            else ->
                "Status update: Your booking for $serviceName is currently $status."
        }
        showNotification(context, title, msg, 101)
    }

    fun notifyPaymentReceived(context: Context, invoiceNumber: String, status: String) {
        if (!isBookingNotificationsEnabled(context)) return
        val title = "Payment Status Updated"
        val msg =
            "Invoice $invoiceNumber payment has been recorded as $status. Thank you for choosing Ily Photo Studio!"
        showNotification(context, title, msg, 102)
    }

    fun notifyGalleryReady(context: Context, eventName: String) {
        if (!isGalleryNotificationsEnabled(context)) return
        val title = "Gallery Photos Ready!"
        val msg =
            "Your high-resolution photographs for '$eventName' have been uploaded. Tap to preview and download."
        showNotification(context, title, msg, 103)
    }

    fun notifyPushEnabled(context: Context) {
        val title = "Notifications Enabled ✓"
        val msg =
            "You will now receive instant push alerts for photoshoot booking updates, reminders, and gallery uploads."
        showNotification(context, title, msg, 100)
    }

    /**
     * Tracks booking status and invoice status across app sessions in SharedPreferences
     * and dispatches push notifications whenever a booking or invoice status changes in Azure.
     */
    fun checkAndNotifyBookingChanges(context: Context, bookings: List<Booking>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (booking in bookings) {
            if (booking.id.isBlank()) continue
            val statusKey = "booking_status_${booking.id}"
            val invoiceKey = "invoice_status_${booking.id}"
            val currentStatus = booking.status.ifBlank { "Pending" }
            val currentInvoice = booking.invoiceStatus.ifBlank { "Unpaid" }

            val prevStatus = prefs.getString(statusKey, null)
            if (prevStatus != null && !prevStatus.equals(currentStatus, ignoreCase = true)) {
                notifyBookingStatus(context, booking.serviceName, currentStatus)
            }
            editor.putString(statusKey, currentStatus)

            val prevInvoice = prefs.getString(invoiceKey, null)
            if (prevInvoice != null && !prevInvoice.equals(currentInvoice, ignoreCase = true)) {
                notifyPaymentReceived(
                    context,
                    booking.invoiceNumber.ifBlank { booking.id },
                    currentInvoice
                )
            }
            editor.putString(invoiceKey, currentInvoice)
        }
        editor.apply()
    }
}
