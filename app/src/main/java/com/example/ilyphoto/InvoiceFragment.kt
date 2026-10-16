package com.example.ilyphoto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.config.BusinessConfig
import com.example.ilyphoto.data.Booking
import com.example.ilyphoto.data.ServiceLocator
import com.example.ilyphoto.data.UserRole
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InvoiceFragment : Fragment(R.layout.fragment_invoice) {

    private var currentBooking: Booking? = null
    private var userBookingsList: List<Booking> = emptyList()
    private var lastKnownStatus: String? = null
    private var lastKnownPaymentStatus: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        val cardEmpty = view.findViewById<View>(R.id.cardEmptyInvoice)
        val cardInvoice = view.findViewById<View>(R.id.cardInvoiceDetails)
        val btnEmptyBook = view.findViewById<TextView>(R.id.btnEmptyBook)

        val tvStatusBadge = view.findViewById<TextView>(R.id.tvInvoiceStatusBadge)
        val tvInvoiceNumber = view.findViewById<TextView>(R.id.tvInvoiceNumber)
        val tvStudioInfo = view.findViewById<TextView>(R.id.tvStudioInfo)
        val tvClientDetails = view.findViewById<TextView>(R.id.tvClientDetails)
        val tvInvoiceService = view.findViewById<TextView>(R.id.tvInvoiceService)
        val tvInvoiceDateTime = view.findViewById<TextView>(R.id.tvInvoiceDateTime)
        val tvInvoiceLocation = view.findViewById<TextView>(R.id.tvInvoiceLocation)
        val tvInvoiceDuration = view.findViewById<TextView>(R.id.tvInvoiceDuration)
        val tvInvoiceTotalAmount = view.findViewById<TextView>(R.id.tvInvoiceTotalAmount)
        val tvBankInfo = view.findViewById<TextView>(R.id.tvBankInfo)

        val btnCopyBank = view.findViewById<TextView>(R.id.btnCopyBankDetails)
        val btnDownload = view.findViewById<TextView>(R.id.btnDownloadInvoice)
        val btnRecordPayment = view.findViewById<TextView>(R.id.btnRecordPayment)

        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        // Set business info
        tvStudioInfo?.text = "${BusinessConfig.BUSINESS_NAME} • ${BusinessConfig.OWNER_NAME}\n" +
                "${BusinessConfig.BUSINESS_ADDRESS}\n" +
                "${BusinessConfig.CONTACT_PHONE} • ${BusinessConfig.CONTACT_EMAIL}"

        // Strictly restrict "Record Payment" to Admin/Photographer only
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.auth.currentUser.collectLatest { profile ->
                val isAdmin = profile?.role == UserRole.PHOTOGRAPHER
                btnRecordPayment?.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }
        }

        fun renderBookingInvoice(activeBooking: Booking) {
            currentBooking = activeBooking

            val invNum = activeBooking.invoiceNumber.ifBlank { "INV-" + activeBooking.id.take(6).uppercase() }
            val invoiceHeader = if (userBookingsList.size > 1) "$invNum (Tap to switch)" else invNum
            tvInvoiceNumber?.text = invoiceHeader

            // Configure status badge
            val status = activeBooking.invoiceStatus.ifBlank { "Unpaid" }
            tvStatusBadge?.text = status.uppercase()
            when (status.lowercase()) {
                "paid" -> tvStatusBadge?.setBackgroundResource(R.drawable.badge_paid)
                "partially paid" -> tvStatusBadge?.setBackgroundResource(R.drawable.badge_partially_paid)
                else -> tvStatusBadge?.setBackgroundResource(R.drawable.badge_unpaid)
            }

            // Client Info
            val clientName = activeBooking.clientName.ifBlank { "Valued Client" }
            val clientContact = listOfNotNull(
                activeBooking.clientEmail.takeIf { it.isNotBlank() },
                activeBooking.clientPhone.takeIf { it.isNotBlank() }
            ).joinToString(" • ").ifBlank { "Strandfontein Client" }
            tvClientDetails?.text = "$clientName\n$clientContact"

            // Service Details
            tvInvoiceService?.text = activeBooking.serviceName.ifBlank { "Photography Session" }
            tvInvoiceDateTime?.text = "${activeBooking.eventDate} @ ${activeBooking.eventTime.ifBlank { "14:00" }}"
            tvInvoiceLocation?.text = activeBooking.eventLocation.ifBlank { "Strandfontein, Cape Town" }
            tvInvoiceDuration?.text = "${activeBooking.durationHours} Hours Session"
            tvInvoiceTotalAmount?.text = "R${activeBooking.servicePrice}.00"

            // Bank Details
            tvBankInfo?.text = "Bank: ${BusinessConfig.bank.bankName}\n" +
                    "Account Holder: ${BusinessConfig.bank.accountHolder}\n" +
                    "Account No: ${BusinessConfig.bank.accountNumber}\n" +
                    "Branch Code: ${BusinessConfig.bank.branchCode}\n" +
                    "Reference: $invNum"

            // Auto status update notifications
            if (lastKnownPaymentStatus != null && lastKnownPaymentStatus != activeBooking.invoiceStatus) {
                IlyNotificationHelper.notifyPaymentReceived(requireContext(), invNum, activeBooking.invoiceStatus)
            }
            if (lastKnownStatus != null && lastKnownStatus != activeBooking.status) {
                IlyNotificationHelper.notifyBookingStatus(requireContext(), activeBooking.serviceName, activeBooking.status)
            }
            lastKnownPaymentStatus = activeBooking.invoiceStatus
            lastKnownStatus = activeBooking.status
        }

        val currentUid = ServiceLocator.auth.currentUserSnapshot()?.uid
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.bookings.bookings().collectLatest { allBookings ->
                val profile = ServiceLocator.auth.currentUser
                val userBookings = if (!currentUid.isNullOrEmpty()) {
                    allBookings.filter { it.clientId == currentUid }
                } else {
                    emptyList()
                }

                userBookingsList = userBookings

                val activeBooking = currentBooking?.let { cur ->
                    userBookings.firstOrNull { it.id == cur.id }
                } ?: userBookings.firstOrNull()

                if (activeBooking == null) {
                    cardEmpty?.visibility = View.VISIBLE
                    cardInvoice?.visibility = View.GONE
                } else {
                    cardEmpty?.visibility = View.GONE
                    cardInvoice?.visibility = View.VISIBLE
                    renderBookingInvoice(activeBooking)
                }
            }
        }

        // Tap Invoice Number to switch between multiple bookings/invoices
        tvInvoiceNumber?.setOnClickListener {
            if (userBookingsList.size > 1) {
                val invoiceOptions = userBookingsList.map {
                    "${it.invoiceNumber} • ${it.serviceName} (${it.eventDate}) [${it.invoiceStatus}]"
                }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Select Invoice")
                    .setItems(invoiceOptions) { _, which ->
                        renderBookingInvoice(userBookingsList[which])
                    }
                    .show()
            }
        }

        btnEmptyBook?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_bookingFragment)
        }

        btnCopyBank?.setOnClickListener {
            val booking = currentBooking
            val ref = booking?.invoiceNumber?.ifBlank { "INV-ILY" } ?: "INV-ILY"
            val textToCopy = "Bank: ${BusinessConfig.bank.bankName}\n" +
                    "Account: ${BusinessConfig.bank.accountHolder}\n" +
                    "Account No: ${BusinessConfig.bank.accountNumber}\n" +
                    "Branch: ${BusinessConfig.bank.branchCode}\n" +
                    "Reference: $ref"

            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Ily Photo Bank Details", textToCopy)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(requireContext(), "Banking details copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Real PDF download saving to device
        btnDownload?.setOnClickListener {
            val booking = currentBooking
            if (booking != null) {
                val savedFile = InvoicePdfHelper.generateAndSaveInvoicePdf(requireContext(), booking)
                if (savedFile != null) {
                    Toast.makeText(
                        requireContext(),
                        "✓ Invoice saved to device Downloads: ${savedFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Could not generate PDF", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No active invoice found to download", Toast.LENGTH_SHORT).show()
            }
        }

        // Only accessible to photographer/admin
        btnRecordPayment?.setOnClickListener {
            val booking = currentBooking
            if (booking == null) {
                Toast.makeText(requireContext(), "No booking available to update", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val options = arrayOf(
                "Paid (EFT / Card / Cash)",
                "Partially Paid (50% Deposit)",
                "Unpaid"
            )

            AlertDialog.Builder(requireContext())
                .setTitle("Update Payment Status for ${booking.invoiceNumber}")
                .setItems(options) { _, which ->
                    val newStatus = when (which) {
                        0 -> "Paid"
                        1 -> "Partially Paid"
                        else -> "Unpaid"
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        ServiceLocator.bookings.updateInvoiceStatus(booking, newStatus)
                        Toast.makeText(requireContext(), "Invoice marked as $newStatus!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Bottom Navigation Bar
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
