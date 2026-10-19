package com.example.ilyphoto

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.AzureProvider
import com.example.ilyphoto.data.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnTopProfile = view.findViewById<View>(R.id.btnTopProfile)
        val ivTopAvatar = view.findViewById<ImageView>(R.id.ivTopProfileAvatar)

        btnTopProfile?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_profileFragment)
        }

        val homeTagline = view.findViewById<TextView>(R.id.homeTagline)
        val upcomingEventsContainer = view.findViewById<LinearLayout>(R.id.upcomingEventsContainer)
        val cardNoUpcoming = view.findViewById<View>(R.id.cardNoUpcoming)
        val btnBookSessionEmpty = view.findViewById<View>(R.id.btnBookSessionEmpty)
        val btnViewBookings = view.findViewById<TextView>(R.id.btnViewBookings)

        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        // Personalized User Session Header & Avatar from Database
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.auth.currentUser.collectLatest { profile ->
                ProfileAvatarHelper.loadAvatar(requireContext(), ivTopAvatar, profile)
                if (profile != null && profile.displayName.isNotBlank()) {
                    homeTagline?.text = "WELCOME, ${profile.displayName.uppercase()}"
                } else {
                    homeTagline?.text = getString(R.string.capturing_every_moment)
                }
            }
        }

        // Live user-scoped booking observation: display ALL upcoming events
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.bookings.bookings().collectLatest { allBookings ->
                val currentProfile = ServiceLocator.auth.currentUserSnapshot()
                    ?: AzureProvider.getCachedProfile()
                val userBookings = if (currentProfile != null) {
                    allBookings.filter { currentProfile.matchesBooking(it) }
                } else {
                    allBookings
                }

                // Dispatch notifications whenever status or invoice status updates in Azure
                if (currentProfile != null) {
                    IlyNotificationHelper.checkAndNotifyBookingChanges(requireContext(), userBookings)
                }

                // Filter for upcoming (non-cancelled or active bookings)
                val upcomingBookings = userBookings.filter {
                    !it.status.equals("Cancelled", ignoreCase = true)
                }

                // Remove existing inflated cards while keeping the cardNoUpcoming empty state view
                val viewsToRemove = mutableListOf<View>()
                for (i in 0 until (upcomingEventsContainer?.childCount ?: 0)) {
                    val child = upcomingEventsContainer?.getChildAt(i)
                    if (child?.id != R.id.cardNoUpcoming) {
                        child?.let { viewsToRemove.add(it) }
                    }
                }
                viewsToRemove.forEach { upcomingEventsContainer?.removeView(it) }

                if (upcomingBookings.isEmpty()) {
                    cardNoUpcoming?.visibility = View.VISIBLE
                } else {
                    cardNoUpcoming?.visibility = View.GONE

                    val inflater = LayoutInflater.from(requireContext())
                    for (booking in upcomingBookings) {
                        val cardView = inflater.inflate(
                            R.layout.item_upcoming_event_card,
                            upcomingEventsContainer,
                            false
                        )

                        val tvUpcomingServiceName = cardView.findViewById<TextView>(R.id.tvUpcomingServiceName)
                        val tvUpcomingStatusBadge = cardView.findViewById<TextView>(R.id.tvUpcomingStatusBadge)
                        val tvUpcomingDateTime = cardView.findViewById<TextView>(R.id.tvUpcomingDateTime)
                        val tvUpcomingLocation = cardView.findViewById<TextView>(R.id.tvUpcomingLocation)
                        val tvUpcomingCountdown = cardView.findViewById<TextView>(R.id.tvUpcomingCountdown)
                        val btnUpcomingMore = cardView.findViewById<TextView>(R.id.btnUpcomingMore)

                        tvUpcomingServiceName?.text = booking.serviceName.ifBlank { "Photoshoot Session" }

                        // Status Badge formatting with high-contrast white text
                        val status = booking.status.ifBlank { "Pending" }
                        tvUpcomingStatusBadge?.text = status.uppercase()
                        tvUpcomingStatusBadge?.setTextColor(Color.WHITE)
                        when (status.lowercase()) {
                            "approved", "confirmed", "completed" -> {
                                tvUpcomingStatusBadge?.setBackgroundResource(R.drawable.badge_paid)
                            }
                            "pending", "rescheduled" -> {
                                tvUpcomingStatusBadge?.setBackgroundResource(R.drawable.badge_pending)
                            }
                            else -> {
                                tvUpcomingStatusBadge?.setBackgroundResource(R.drawable.badge_cancelled)
                            }
                        }

                        // Date & Location
                        val timeStr = booking.eventTime.ifBlank { "14:00" }
                        tvUpcomingDateTime?.text = "📅 ${booking.eventDate} • $timeStr"
                        tvUpcomingLocation?.text = "📍 ${booking.eventLocation.ifBlank { "Strandfontein, Cape Town" }}"

                        // Live Countdown calculation
                        tvUpcomingCountdown?.text = calculateCountdown(booking.eventDate, timeStr)

                        btnUpcomingMore?.setOnClickListener {
                            findNavController().navigate(R.id.action_homeFragment_to_myBookingFragment)
                        }

                        cardView.setOnClickListener {
                            findNavController().navigate(R.id.action_homeFragment_to_myBookingFragment)
                        }

                        upcomingEventsContainer?.addView(cardView)
                    }
                }
            }
        }

        btnBookSessionEmpty?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_bookingFragment)
        }

        btnViewBookings?.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_myBookingFragment)
        }

        navHome?.setOnClickListener {
            // Already on Home screen
        }

        navSettings?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_settingsFragment)
        }

        navMenu?.setOnClickListener {
            NavigationMenuDialogFragment().show(parentFragmentManager, "NavMenu")
        }
    }

    private fun calculateCountdown(dateStr: String, timeStr: String): String {
        if (dateStr.isBlank()) return "Date to be confirmed"
        val formats = listOf(
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH)
        )
        var parsedDate: Date? = null
        for (fmt in formats) {
            try {
                parsedDate = fmt.parse(dateStr.trim())
                if (parsedDate != null) break
            } catch (_: Exception) {}
        }
        if (parsedDate == null) {
            return "⏳ Scheduled on $dateStr"
        }

        val cal = Calendar.getInstance().apply {
            time = parsedDate
            val timeParts = timeStr.trim().split(":")
            if (timeParts.size >= 2) {
                set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 12)
                set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
            } else {
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
            }
        }

        val targetMillis = cal.timeInMillis
        val now = System.currentTimeMillis()
        val diff = targetMillis - now

        return when {
            diff > 0 -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                when {
                    days > 1 -> "⏳ $days days remaining"
                    days == 1L -> "⏳ 1 day ($hours hrs remaining) • Tomorrow!"
                    hours > 0 -> "⏳ $hours hrs remaining • Today!"
                    else -> "🎉 Photo session is TODAY!"
                }
            }
            diff > -86400000L -> "🎉 Photo session is TODAY!"
            else -> "✓ Event Completed"
        }
    }
}
