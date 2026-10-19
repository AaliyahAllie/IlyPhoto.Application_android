package com.example.ilyphoto

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.Booking
import com.example.ilyphoto.data.ServiceLocator
import com.example.ilyphoto.data.UserProfile
import com.example.ilyphoto.data.toUserMessage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingFragment : Fragment(R.layout.fragment_booking) {

    private data class PackageOption(
        val name: String,
        val basePrice: Int,
        val baseHours: Int,
        val extraHourlyRate: Int,
        val description: String
    )

    private val packageOptions = listOf(
        PackageOption("Matric Ball", 400, 2, 150, "R400 for 2 Hours (R150/extra hr)"),
        PackageOption("Weddings", 1200, 6, 200, "R1200 for 6 Hours (R200/extra hr)"),
        PackageOption("Events", 400, 2, 150, "R400 for 2 Hours (R150/extra hr)"),
        PackageOption("Portraits", 300, 1, 150, "R300 for 1 Hour (R150/extra hr)"),
        PackageOption("Community / NGO Outreach", 200, 2, 100, "R200 for 2 Hours (Subsidised NGO & School Rate)")
    )

    private var selectedPackage = packageOptions[0]
    private var durationHours: Int = 2
    private var servicePrice: Int = 400

    // Calendar initialized to tomorrow at 14:00 (accurate day-of-week & infinite picker)
    private var selectedCalendar: Calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editingBookingId = arguments?.getString("bookingId")
        val initialPackageName = arguments?.getString("selectedPackageName")

        // Top Right Profile Button
        val btnTopProfile = view.findViewById<View>(R.id.btnTopProfile)
        val ivTopAvatar = view.findViewById<ImageView>(R.id.ivTopProfileAvatar)
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.auth.currentUser.collectLatest { profile ->
                ProfileAvatarHelper.loadAvatar(requireContext(), ivTopAvatar, profile)
            }
        }
        btnTopProfile?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_profileFragment)
        }

        // Header and Date & Time Picker Views
        val tvCalendarYear = view.findViewById<TextView>(R.id.tvCalendarYear)
        val tvCalendarSelectedDate = view.findViewById<TextView>(R.id.tvCalendarSelectedDate)
        val btnOpenDatePicker = view.findViewById<View>(R.id.btnOpenDatePicker)
        val tvChosenDate = view.findViewById<TextView>(R.id.tvChosenDate)
        val btnOpenTimePicker = view.findViewById<View>(R.id.btnOpenTimePicker)
        val tvChosenTime = view.findViewById<TextView>(R.id.tvChosenTime)
        val tvDateAvailability = view.findViewById<TextView>(R.id.tvDateAvailability)

        // Customization Views
        val tvCustomizationHeading = view.findViewById<TextView>(R.id.tvCustomizationHeading)
        val tvSelectedPackage = view.findViewById<TextView>(R.id.tvSelectedPackage)
        val btnDecreaseHours = view.findViewById<TextView>(R.id.btnDecreaseHours)
        val btnIncreaseHours = view.findViewById<TextView>(R.id.btnIncreaseHours)
        val tvHoursValue = view.findViewById<TextView>(R.id.tvHoursValue)
        val tvCalculatedPrice = view.findViewById<TextView>(R.id.tvCalculatedPrice)
        val etBookingLocation = view.findViewById<EditText>(R.id.etBookingLocation)
        val etBookingPhone = view.findViewById<EditText>(R.id.etBookingPhone)
        val etBookingRequirements = view.findViewById<EditText>(R.id.etBookingRequirements)
        val btnMakeBooking = view.findViewById<TextView>(R.id.btnMakeBooking)

        // Conflict Dialog Overlay
        val conflictOverlay = view.findViewById<FrameLayout>(R.id.conflictOverlay)
        val btnConflictClose = view.findViewById<TextView>(R.id.btnConflictClose)

        // Navigation
        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        // Apply pre-selected package from Packages screen if not editing an existing booking
        if (editingBookingId.isNullOrEmpty() && !initialPackageName.isNullOrBlank()) {
            packageOptions.firstOrNull {
                it.name.equals(initialPackageName, ignoreCase = true) ||
                    initialPackageName.contains(it.name, ignoreCase = true)
            }?.let { matched ->
                selectedPackage = matched
                durationHours = matched.baseHours
                tvSelectedPackage?.text = "${matched.name} (${matched.description})"
            }
        }

        fun updatePriceAndHours() {
            val extraHours = maxOf(0, durationHours - selectedPackage.baseHours)
            servicePrice = selectedPackage.basePrice + (extraHours * selectedPackage.extraHourlyRate)
            tvHoursValue?.text = "$durationHours Hours"
            tvCalculatedPrice?.text = "R$servicePrice.00"
        }

        fun updateCalendarAndDateViews() {
            val yearFormat = SimpleDateFormat("yyyy", Locale.ENGLISH)
            val fullDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
            val timeAmPmFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

            tvCalendarYear?.text = yearFormat.format(selectedCalendar.time)
            val fullDateStr = fullDateFormat.format(selectedCalendar.time)
            tvCalendarSelectedDate?.text = fullDateStr
            tvChosenDate?.text = "📅 $fullDateStr"

            val time24 = timeFormat.format(selectedCalendar.time)
            val timeAmPm = timeAmPmFormat.format(selectedCalendar.time)
            tvChosenTime?.text = "⏰ $time24 ($timeAmPm)"

            val eventDateKey = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(selectedCalendar.time)
            tvDateAvailability?.text = "⏳ Checking live Azure schedule..."
            tvDateAvailability?.setBackgroundResource(R.drawable.badge_countdown)
            tvDateAvailability?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_text_dark))

            viewLifecycleOwner.lifecycleScope.launch {
                val isAvailable = runCatching {
                    ServiceLocator.bookings.checkAvailability(
                        eventDate = eventDateKey,
                        excludeBookingId = editingBookingId
                    )
                }.getOrDefault(true)

                if (!isAvailable) {
                    tvDateAvailability?.text = "⚠️ Date Already Booked — Please Select Another Date"
                    tvDateAvailability?.setBackgroundResource(R.drawable.badge_pending)
                    tvDateAvailability?.setTextColor(Color.WHITE)
                } else {
                    tvDateAvailability?.text = "✓ Photographer Available on this Date"
                    tvDateAvailability?.setBackgroundResource(R.drawable.badge_countdown)
                    tvDateAvailability?.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_text_dark))
                }
            }
        }

        updateCalendarAndDateViews()
        updatePriceAndHours()

        // 1. Date Picker Listener (Infinite & accurately calculates day of the week)
        btnOpenDatePicker?.setOnClickListener {
            val currentYear = selectedCalendar.get(Calendar.YEAR)
            val currentMonth = selectedCalendar.get(Calendar.MONTH)
            val currentDay = selectedCalendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedCalendar.set(Calendar.YEAR, year)
                    selectedCalendar.set(Calendar.MONTH, month)
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    updateCalendarAndDateViews()
                },
                currentYear,
                currentMonth,
                currentDay
            )
            dpd.datePicker.minDate = System.currentTimeMillis() - 1000
            dpd.show()
        }

        // 2. Time Picker Listener
        btnOpenTimePicker?.setOnClickListener {
            val currentHour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = selectedCalendar.get(Calendar.MINUTE)

            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    selectedCalendar.set(Calendar.MINUTE, minute)
                    updateCalendarAndDateViews()
                },
                currentHour,
                currentMinute,
                true
            ).show()
        }

        // If editing an existing booking, load its data and update UI
        if (!editingBookingId.isNullOrEmpty()) {
            tvCustomizationHeading?.text = "RESCHEDULE & EDIT BOOKING"
            btnMakeBooking?.text = "UPDATE BOOKING"

            viewLifecycleOwner.lifecycleScope.launch {
                val existing = ServiceLocator.bookings.getBookingById(editingBookingId)
                if (existing != null) {
                    val matchedPkg = packageOptions.firstOrNull {
                        it.name.equals(existing.serviceName, ignoreCase = true) ||
                            existing.serviceName.contains(it.name, ignoreCase = true)
                    } ?: packageOptions[0]
                    selectedPackage = matchedPkg
                    durationHours = existing.durationHours.coerceAtLeast(1)
                    servicePrice = existing.servicePrice

                    runCatching {
                        val normalizedIso = Booking.normalizeDateKey(existing.eventDate)
                        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
                        val parsed = runCatching { isoFormat.parse(normalizedIso) }.getOrNull()
                            ?: runCatching { displayFormat.parse(existing.eventDate) }.getOrNull()
                        if (parsed != null) {
                            val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
                            val min = selectedCalendar.get(Calendar.MINUTE)
                            selectedCalendar.time = parsed
                            selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                            selectedCalendar.set(Calendar.MINUTE, min)
                        }
                    }
                    runCatching {
                        val timeParts = existing.eventTime.split(":")
                        if (timeParts.size >= 2) {
                            selectedCalendar.set(Calendar.HOUR_OF_DAY, timeParts[0].trim().toInt())
                            selectedCalendar.set(Calendar.MINUTE, timeParts[1].trim().toInt())
                        }
                    }

                    etBookingLocation?.setText(existing.eventLocation)
                    etBookingPhone?.setText(existing.clientPhone)
                    etBookingRequirements?.setText(existing.requirements)
                    tvSelectedPackage?.text = "${selectedPackage.name} (${selectedPackage.description})"

                    updateCalendarAndDateViews()
                    updatePriceAndHours()
                }
            }
        }

        // Package selection dialog
        tvSelectedPackage?.setOnClickListener {
            val packageNames = packageOptions.map { "${it.name} (${it.description})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Package / Event Type")
                .setItems(packageNames) { _, which ->
                    selectedPackage = packageOptions[which]
                    tvSelectedPackage.text = "${selectedPackage.name} (${selectedPackage.description})"
                    if (durationHours < selectedPackage.baseHours) {
                        durationHours = selectedPackage.baseHours
                    }
                    updatePriceAndHours()
                }
                .show()
        }

        // Hours stepper
        btnDecreaseHours?.setOnClickListener {
            if (durationHours > selectedPackage.baseHours) {
                durationHours--
                updatePriceAndHours()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Minimum duration for ${selectedPackage.name} is ${selectedPackage.baseHours} hours",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnIncreaseHours?.setOnClickListener {
            if (durationHours < 12) {
                durationHours++
                updatePriceAndHours()
            } else {
                Toast.makeText(requireContext(), "Maximum booking duration is 12 hours", Toast.LENGTH_SHORT).show()
            }
        }

        btnConflictClose?.setOnClickListener {
            conflictOverlay?.visibility = View.GONE
        }

        // Make Booking / Update Booking submission
        btnMakeBooking?.setOnClickListener {
            val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(selectedCalendar.time)
            val formattedTime = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(selectedCalendar.time)

            val location = etBookingLocation?.text?.toString()?.trim().orEmpty().ifEmpty {
                "Strandfontein, Cape Town"
            }
            val requirements = etBookingRequirements?.text?.toString()?.trim().orEmpty().ifEmpty {
                "Standard photography session (${selectedPackage.name})"
            }

            btnMakeBooking.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val userProfile: UserProfile? = ServiceLocator.auth.currentUser.firstOrNull()
                val phone = etBookingPhone?.text?.toString()?.trim().orEmpty().ifEmpty {
                    userProfile?.phone.orEmpty().ifEmpty { "0848993280" }
                }

                if (!editingBookingId.isNullOrEmpty()) {
                    // Persist all amended fields (date, time, venue, package, price, duration, requirements) to Azure
                    val result = ServiceLocator.bookings.updateBooking(
                        bookingId = editingBookingId,
                        serviceName = selectedPackage.name,
                        servicePrice = servicePrice,
                        durationHours = durationHours,
                        eventDate = formattedDate,
                        eventTime = formattedTime,
                        eventLocation = location,
                        requirements = requirements,
                        clientPhone = phone
                    )
                    btnMakeBooking.isEnabled = true

                    result.onSuccess { updatedBooking ->
                        IlyNotificationHelper.notifyBookingRescheduled(
                            requireContext(),
                            updatedBooking.serviceName,
                            updatedBooking.eventDate,
                            updatedBooking.eventTime,
                            updatedBooking.eventLocation
                        )
                        Toast.makeText(
                            requireContext(),
                            "Booking updated for ${updatedBooking.eventDate} at ${updatedBooking.eventTime} (${updatedBooking.eventLocation})!",
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().navigate(R.id.action_bookingFragment_to_myBookingFragment)
                    }.onFailure { err ->
                        val msg = err.toUserMessage()
                        if (msg.contains("OOPS", ignoreCase = true) || msg.contains("booked", ignoreCase = true)) {
                            conflictOverlay?.visibility = View.VISIBLE
                        } else {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    // Create fresh new booking after authoritative cloud conflict verification
                    val result = ServiceLocator.bookings.createBooking(
                        clientProfile = userProfile,
                        clientPhone = phone,
                        serviceName = selectedPackage.name,
                        servicePrice = servicePrice,
                        durationHours = durationHours,
                        eventDate = formattedDate,
                        eventTime = formattedTime,
                        eventLocation = location,
                        requirements = requirements
                    )
                    btnMakeBooking.isEnabled = true

                    result.onSuccess { booking ->
                        IlyNotificationHelper.notifyBookingCreated(
                            requireContext(),
                            booking.serviceName,
                            booking.eventDate,
                            booking.invoiceNumber
                        )
                        Toast.makeText(
                            requireContext(),
                            "Booking confirmed for $formattedDate! Invoice: ${booking.invoiceNumber}",
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().navigate(R.id.action_bookingFragment_to_myBookingFragment)
                    }.onFailure { err ->
                        val msg = err.toUserMessage()
                        if (msg.contains("OOPS", ignoreCase = true) || msg.contains("booked", ignoreCase = true)) {
                            conflictOverlay?.visibility = View.VISIBLE
                        } else {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        navHome?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_homeFragment)
        }

        navSettings?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_settingsFragment)
        }

        navMenu?.setOnClickListener {
            NavigationMenuDialogFragment().show(parentFragmentManager, "NavMenu")
        }
    }
}
