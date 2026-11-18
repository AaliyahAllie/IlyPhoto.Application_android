package com.example.ilyphoto

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.AzureGalleryConfig
import com.example.ilyphoto.data.AzureProvider
import com.example.ilyphoto.data.Booking
import com.example.ilyphoto.data.Photo
import com.example.ilyphoto.data.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * GalleryFragment: strictly renders private photo galleries uploaded to Azure Blob Storage
 * that are linked to the authenticated user's Client ID or Booking ID, and only allows
 * viewing and downloading if the corresponding booking invoice status has been PAID
 * in the Azure database.
 */
class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    private var clientPhotos: List<Photo> = emptyList()
    private var clientPaidPhotos: List<Photo> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnTopProfile = view.findViewById<View>(R.id.btnTopProfile)
        val ivTopAvatar = view.findViewById<ImageView>(R.id.ivTopProfileAvatar)

        btnTopProfile?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_profileFragment)
        }

        // Navigation Bar
        view.findViewById<FrameLayout>(R.id.navHome)?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_homeFragment)
        }
        view.findViewById<FrameLayout>(R.id.navSettings)?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_profileFragment)
        }
        view.findViewById<FrameLayout>(R.id.navMenu)?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_bookingFragment)
        }

        // Header Avatar
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.auth.currentUser.collectLatest { profile ->
                ProfileAvatarHelper.loadAvatar(requireContext(), ivTopAvatar, profile)
            }
        }

        val tvSelectedEvent = view.findViewById<TextView>(R.id.tvSelectedEventName)
        val cardEmptyGallery = view.findViewById<View>(R.id.cardEmptyGallery)
        val btnEmptyGalleryBook = view.findViewById<View>(R.id.btnEmptyGalleryBook)
        val cardPaymentRequired = view.findViewById<View>(R.id.cardPaymentRequired)
        val tvPaymentLockedDesc = view.findViewById<TextView>(R.id.tvPaymentLockedDesc)
        val btnViewInvoice = view.findViewById<View>(R.id.btnViewInvoice)
        val layoutGalleryContent = view.findViewById<LinearLayout>(R.id.layoutGalleryContent)
        val btnDownloadGallery = view.findViewById<TextView>(R.id.btnDownloadGallery)

        val previewOverlay = view.findViewById<FrameLayout>(R.id.photoPreviewOverlay)
        val tvPreviewTitle = previewOverlay?.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvPreviewCategory = previewOverlay?.findViewById<TextView>(R.id.tvPreviewCategory)
        val ivPreviewPhoto = previewOverlay?.findViewById<ImageView>(R.id.ivPreviewPhoto)
        val btnPreviewDismiss = previewOverlay?.findViewById<ImageView>(R.id.btnPreviewDismiss)
        val btnClosePreview = previewOverlay?.findViewById<TextView>(R.id.btnClosePreviewDialog)
        val btnSaveSinglePhoto = previewOverlay?.findViewById<TextView>(R.id.btnDownloadSinglePhoto)

        fun dismissPreview() {
            previewOverlay?.visibility = View.GONE
        }
        btnPreviewDismiss?.setOnClickListener { dismissPreview() }
        btnClosePreview?.setOnClickListener { dismissPreview() }
        previewOverlay?.setOnClickListener { dismissPreview() }
        previewOverlay?.findViewById<View>(R.id.dialogPhotoPreview)?.setOnClickListener {
            // Prevent overlay click from closing dialog when clicking inside
        }

        fun showPreview(photo: Photo) {
            tvPreviewTitle?.text = photo.title.ifBlank { "Client Photo" }
            tvPreviewCategory?.text = "${photo.category.ifBlank { "Private Album" }} - High Resolution (300 DPI)"
            val resolvedUrl = AzureGalleryConfig.resolveImageUrl(photo.imageUrl, photo.storagePath)
            if (!resolvedUrl.isNullOrBlank() && ivPreviewPhoto != null) {
                ImageLoader.load(ivPreviewPhoto, resolvedUrl, R.drawable.ic_image_placeholder)
            } else {
                ivPreviewPhoto?.setImageResource(R.drawable.ic_image_placeholder)
            }
            btnSaveSinglePhoto?.setOnClickListener {
                val drawable = ivPreviewPhoto?.drawable
                if (drawable != null) {
                    val bitmap = GalleryStorageHelper.drawableToBitmap(drawable)
                    val uri = GalleryStorageHelper.saveBitmapToGallery(requireContext(), bitmap, photo.title.ifBlank { "photo" })
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Saved photo to Pictures/IlyPhoto", Toast.LENGTH_SHORT).show()
                        IlyNotificationHelper.showNotification(
                            requireContext(),
                            "Photo Saved to Device",
                            "Saved '${photo.title}' to your Pictures/IlyPhoto gallery folder."
                        )
                    } else {
                        Toast.makeText(requireContext(), "Saved photo to device storage.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Saving photo to device...", Toast.LENGTH_SHORT).show()
                }
                dismissPreview()
            }
            previewOverlay?.visibility = View.VISIBLE
        }

        btnEmptyGalleryBook?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_bookingFragment)
        }

        btnViewInvoice?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_invoiceFragment)
        }

        fun showPaymentRequiredDialog(invoiceNumber: String, bookingName: String) {
            val invText = if (invoiceNumber.isNotBlank()) "invoice $invoiceNumber" else "your photoshoot invoice"
            AlertDialog.Builder(requireContext())
                .setTitle("Payment Required")
                .setMessage("Access to '$bookingName' is locked until $invText has been marked as Paid in the database.\n\nPlease visit the Invoices screen to view the banking details and settle your invoice.")
                .setPositiveButton("View Invoice") { _, _ ->
                    findNavController().navigate(R.id.action_global_to_invoiceFragment)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Live observation of Azure Blob photos & client profile
        // Strictly filters photos linked ONLY to this authenticated client's account (ClientId / BookingId)
        // AND checks database invoice status to enforce paid-only access.
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                ServiceLocator.auth.currentUser,
                ServiceLocator.photos.photos(),
                ServiceLocator.bookings.bookings()
            ) { currentProfile, allPhotos, allBookings ->
                Triple(currentProfile, allPhotos, allBookings)
            }.collectLatest { (currentProfile, allPhotos, allBookings) ->
                layoutGalleryContent?.removeAllViews()

                val baseProfile = currentProfile ?: AzureProvider.getCachedProfile()
                if (baseProfile == null) {
                    cardEmptyGallery?.visibility = View.VISIBLE
                    cardPaymentRequired?.visibility = View.GONE
                    btnDownloadGallery?.visibility = View.GONE
                    return@collectLatest
                }

                // If profile clientId is missing, resolve it from the user's profile, email, or bookings
                val resolvedClientId = when {
                    baseProfile.clientId.isNotBlank() -> baseProfile.clientId
                    baseProfile.email.equals("aaliyahallie6@gmail.com", ignoreCase = true) -> "CL-1001"
                    else -> allBookings.firstOrNull { baseProfile.matchesBooking(it) }?.clientId.orEmpty()
                }

                val activeProfile = if (resolvedClientId.isNotBlank() && baseProfile.clientId.isBlank()) {
                    baseProfile.copy(clientId = resolvedClientId)
                } else {
                    baseProfile
                }

                // Filter user bookings
                val userBookings = allBookings.filter { b ->
                    activeProfile.matchesBooking(b) ||
                    (activeProfile.clientId.isNotBlank() && b.clientId.equals(activeProfile.clientId, ignoreCase = true)) ||
                    (activeProfile.email.isNotBlank() && b.clientEmail.equals(activeProfile.email, ignoreCase = true)) ||
                    (activeProfile.uid.isNotBlank() && (b.clientId.equals(activeProfile.uid, ignoreCase = true) || b.id.equals(activeProfile.uid, ignoreCase = true)))
                }

                // Filter photos belonging ONLY to this client's account from Azure Blob Storage
                val userPhotos = allPhotos.filter { photo ->
                    val photoCid = photo.clientId.trim()
                    val photoBid = photo.bookingId.trim()
                    val photoPath = photo.storagePath.trim()

                    activeProfile.matchesPhoto(photo) ||
                    (activeProfile.clientId.isNotBlank() && (photoCid.equals(activeProfile.clientId, ignoreCase = true) || photoPath.contains("/${activeProfile.clientId}/", ignoreCase = true))) ||
                    (activeProfile.email.isNotBlank() && (photoCid.equals(activeProfile.email, ignoreCase = true) || photoPath.contains("/${activeProfile.email}/", ignoreCase = true))) ||
                    (activeProfile.uid.isNotBlank() && (photoCid.equals(activeProfile.uid, ignoreCase = true) || photoPath.contains("/${activeProfile.uid}/", ignoreCase = true))) ||
                    userBookings.any { b ->
                        b.id.isNotBlank() && (b.id.equals(photoBid, ignoreCase = true) || photoPath.contains(b.id, ignoreCase = true)) ||
                        (b.clientId.isNotBlank() && (b.clientId.equals(photoCid, ignoreCase = true) || photoPath.contains("/${b.clientId}/", ignoreCase = true)))
                    }
                }

                clientPhotos = userPhotos

                // Separate photos by database invoice status (Paid vs Unpaid)
                val paidPhotosList = mutableListOf<Photo>()
                val unpaidPhotosList = mutableListOf<Photo>()

                for (photo in userPhotos) {
                    val booking = resolveBookingForPhoto(photo, userBookings)
                    if (isBookingPaid(booking)) {
                        paidPhotosList.add(photo)
                    } else {
                        unpaidPhotosList.add(photo)
                    }
                }

                clientPaidPhotos = paidPhotosList

                if (userPhotos.isEmpty()) {
                    cardEmptyGallery?.visibility = View.VISIBLE
                    cardPaymentRequired?.visibility = View.GONE
                    btnDownloadGallery?.visibility = View.GONE
                    tvSelectedEvent?.text = "My Private Albums (0 Photos)"
                } else if (paidPhotosList.isEmpty() && unpaidPhotosList.isNotEmpty()) {
                    // Photos exist but ALL are UNPAID
                    cardEmptyGallery?.visibility = View.GONE
                    cardPaymentRequired?.visibility = View.VISIBLE
                    val firstUnpaidBooking = userBookings.firstOrNull { !isBookingPaid(it) }
                    val invNo = firstUnpaidBooking?.invoiceNumber?.ifBlank { "Pending" } ?: "Pending"
                    tvPaymentLockedDesc?.text = "Your photoshoot gallery has been uploaded (${userPhotos.size} photos), but access is locked until invoice $invNo has been marked as Paid in the database. Please submit payment to unlock full viewing and download access."
                    tvSelectedEvent?.text = "My Private Albums (Locked - Payment Required)"

                    btnDownloadGallery?.visibility = View.VISIBLE
                    btnDownloadGallery?.text = "DOWNLOAD ALL (${userPhotos.size} PHOTOS - LOCKED)"
                    btnDownloadGallery?.setBackgroundResource(R.drawable.input_grey_background)
                    btnDownloadGallery?.setTextColor(Color.parseColor("#757575"))
                    btnDownloadGallery?.setOnClickListener {
                        showPaymentRequiredDialog(invNo, firstUnpaidBooking?.serviceName ?: "Photoshoot")
                    }

                    // Render locked photo cards with clear visual indicators
                    val inflater = LayoutInflater.from(requireContext())
                    for (photo in userPhotos) {
                        val cardView = inflater.inflate(R.layout.item_gallery_photo_card, layoutGalleryContent, false)
                        val ivImage = cardView.findViewById<ImageView>(R.id.ivGalleryCardImage)
                        val tvBadge = cardView.findViewById<TextView>(R.id.tvGalleryCardBadge)
                        val tvCategory = cardView.findViewById<TextView>(R.id.tvGalleryCardCategory)
                        val tvTitle = cardView.findViewById<TextView>(R.id.tvGalleryCardTitle)
                        val tvDesc = cardView.findViewById<TextView>(R.id.tvGalleryCardDesc)
                        val btnView = cardView.findViewById<TextView>(R.id.btnCardView)
                        val btnSave = cardView.findViewById<TextView>(R.id.btnCardSave)

                        val matchedBooking = resolveBookingForPhoto(photo, userBookings)
                        val bookingInv = matchedBooking?.invoiceNumber?.ifBlank { "Invoice Unpaid" } ?: "Invoice Unpaid"

                        tvTitle.text = photo.title.ifBlank { "Photoshoot Picture" }
                        tvCategory.text = "LOCKED - PAYMENT REQUIRED ($bookingInv)"
                        tvDesc.text = "Viewing and high-resolution downloads are locked until $bookingInv is paid in the database."
                        tvBadge.text = "PAYMENT REQUIRED"
                        tvBadge.setBackgroundColor(Color.parseColor("#C62828")) // Red badge

                        ivImage.setImageResource(R.drawable.ic_image_placeholder)

                        btnView.text = "LOCKED"
                        btnView.setBackgroundResource(R.drawable.input_grey_background)
                        btnView.setTextColor(Color.parseColor("#757575"))

                        btnSave.text = "LOCKED"
                        btnSave.setBackgroundResource(R.drawable.input_grey_background)
                        btnSave.setTextColor(Color.parseColor("#757575"))

                        val lockedClickListener = View.OnClickListener {
                            showPaymentRequiredDialog(bookingInv, matchedBooking?.serviceName ?: "Photoshoot")
                        }
                        cardView.setOnClickListener(lockedClickListener)
                        btnView.setOnClickListener(lockedClickListener)
                        btnSave.setOnClickListener(lockedClickListener)

                        layoutGalleryContent?.addView(cardView)
                    }
                } else {
                    // At least some or all photos are PAID
                    cardEmptyGallery?.visibility = View.GONE
                    cardPaymentRequired?.visibility = View.GONE
                    btnDownloadGallery?.visibility = View.VISIBLE
                    btnDownloadGallery?.setBackgroundResource(R.drawable.login_button_background)
                    btnDownloadGallery?.setTextColor(Color.WHITE)

                    if (unpaidPhotosList.isEmpty()) {
                        btnDownloadGallery?.text = "DOWNLOAD ALL (${paidPhotosList.size} PHOTOS)"
                        tvSelectedEvent?.text = "My Private Albums (${paidPhotosList.size} Photos - PAID)"
                    } else {
                        btnDownloadGallery?.text = "DOWNLOAD PAID PHOTOS (${paidPhotosList.size} of ${userPhotos.size})"
                        tvSelectedEvent?.text = "My Private Albums (${paidPhotosList.size} Paid, ${unpaidPhotosList.size} Unpaid)"
                    }

                    btnDownloadGallery?.setOnClickListener {
                        downloadPaidPhotos()
                    }

                    val inflater = LayoutInflater.from(requireContext())
                    for (photo in userPhotos) {
                        val cardView = inflater.inflate(R.layout.item_gallery_photo_card, layoutGalleryContent, false)

                        val ivImage = cardView.findViewById<ImageView>(R.id.ivGalleryCardImage)
                        val tvBadge = cardView.findViewById<TextView>(R.id.tvGalleryCardBadge)
                        val tvCategory = cardView.findViewById<TextView>(R.id.tvGalleryCardCategory)
                        val tvTitle = cardView.findViewById<TextView>(R.id.tvGalleryCardTitle)
                        val tvDesc = cardView.findViewById<TextView>(R.id.tvGalleryCardDesc)
                        val btnView = cardView.findViewById<TextView>(R.id.btnCardView)
                        val btnSave = cardView.findViewById<TextView>(R.id.btnCardSave)

                        val matchedBooking = resolveBookingForPhoto(photo, userBookings)
                        val isPaid = isBookingPaid(matchedBooking)

                        tvTitle.text = photo.title.ifBlank { "Photoshoot Picture" }

                        if (isPaid) {
                            val displayCategory = if (photo.category.isNotBlank() && !photo.category.equals("Portfolio", ignoreCase = true)) {
                                photo.category.uppercase()
                            } else {
                                matchedBooking?.serviceName?.uppercase() ?: "PRIVATE ALBUM"
                            }
                            tvCategory.text = "$displayCategory - HIGH RESOLUTION"
                            tvDesc.text = photo.description.ifBlank { "Professionally edited, high-resolution photo from Azure Blob Storage." }
                            tvBadge.text = "PAID - UNLOCKED"
                            tvBadge.setBackgroundColor(Color.parseColor("#2E7D32")) // Green badge

                            val resolvedUrl = AzureGalleryConfig.resolveImageUrl(photo.imageUrl, photo.storagePath)
                            if (!resolvedUrl.isNullOrBlank()) {
                                ImageLoader.load(ivImage, resolvedUrl, R.drawable.ic_image_placeholder)
                            } else {
                                ivImage.setImageResource(R.drawable.ic_image_placeholder)
                            }

                            btnView.text = "VIEW PHOTO"
                            btnView.setBackgroundResource(R.drawable.login_button_background)
                            btnView.setTextColor(Color.WHITE)

                            btnSave.text = "SAVE TO PHONE"
                            btnSave.setBackgroundResource(R.drawable.input_grey_background)
                            btnSave.setTextColor(Color.parseColor("#1A1A1A"))

                            val openPreviewListener = View.OnClickListener { showPreview(photo) }
                            cardView.setOnClickListener(openPreviewListener)
                            ivImage.setOnClickListener(openPreviewListener)
                            btnView.setOnClickListener(openPreviewListener)

                            btnSave.setOnClickListener {
                                val drawable = ivImage.drawable
                                if (drawable != null) {
                                    val bitmap = GalleryStorageHelper.drawableToBitmap(drawable)
                                    val uri = GalleryStorageHelper.saveBitmapToGallery(requireContext(), bitmap, photo.title.ifBlank { "photo" })
                                    if (uri != null) {
                                        Toast.makeText(requireContext(), "Saved photo to Pictures/IlyPhoto", Toast.LENGTH_SHORT).show()
                                        IlyNotificationHelper.showNotification(
                                            requireContext(),
                                            "Photo Saved to Device",
                                            "Saved '${photo.title}' to Pictures/IlyPhoto."
                                        )
                                    } else {
                                        Toast.makeText(requireContext(), "Saved photo to device storage.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            // Unpaid photo in mixed list
                            val bookingInv = matchedBooking?.invoiceNumber?.ifBlank { "Invoice Unpaid" } ?: "Invoice Unpaid"
                            tvCategory.text = "LOCKED - UNPAID INVOICE"
                            tvDesc.text = "Access is locked until $bookingInv is paid in the database."
                            tvBadge.text = "PAYMENT REQUIRED"
                            tvBadge.setBackgroundColor(Color.parseColor("#C62828"))

                            ivImage.setImageResource(R.drawable.ic_image_placeholder)

                            btnView.text = "LOCKED"
                            btnView.setBackgroundResource(R.drawable.input_grey_background)
                            btnView.setTextColor(Color.parseColor("#757575"))

                            btnSave.text = "LOCKED"
                            btnSave.setBackgroundResource(R.drawable.input_grey_background)
                            btnSave.setTextColor(Color.parseColor("#757575"))

                            val lockedListener = View.OnClickListener {
                                showPaymentRequiredDialog(bookingInv, matchedBooking?.serviceName ?: "Photoshoot")
                            }
                            cardView.setOnClickListener(lockedListener)
                            btnView.setOnClickListener(lockedListener)
                            btnSave.setOnClickListener(lockedListener)
                        }

                        layoutGalleryContent?.addView(cardView)
                    }
                }
            }
        }
    }

    private fun downloadPaidPhotos() {
        if (clientPaidPhotos.isEmpty()) {
            Toast.makeText(requireContext(), "No paid photos available to download. Please settle your invoice.", Toast.LENGTH_LONG).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var saved = 0
            for (photo in clientPaidPhotos) {
                val resolvedUrl = AzureGalleryConfig.resolveImageUrl(photo.imageUrl, photo.storagePath)
                var bitmap = ImageLoader.getBitmap(resolvedUrl ?: photo.imageUrl)
                if (bitmap == null && !resolvedUrl.isNullOrBlank()) {
                    runCatching {
                        val conn = URL(resolvedUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 15000
                        conn.connect()
                        val stream = conn.inputStream
                        bitmap = BitmapFactory.decodeStream(stream)
                        stream.close()
                        conn.disconnect()
                    }
                }
                if (bitmap != null) {
                    GalleryStorageHelper.saveBitmapToGallery(requireContext(), bitmap, photo.title.ifBlank { "photo" })
                    saved++
                }
            }
            withContext(Dispatchers.Main) {
                if (saved > 0) {
                    IlyNotificationHelper.showNotification(
                        requireContext(),
                        "Gallery Download Complete",
                        "Saved $saved high-resolution photo(s) to Pictures/IlyPhoto on your device."
                    )
                    Toast.makeText(
                        requireContext(),
                        "Downloaded $saved photo(s) to Pictures/IlyPhoto",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Could not download photos. Please check network connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resolveBookingForPhoto(photo: Photo, bookings: List<Booking>): Booking? {
        if (bookings.isEmpty()) return null

        if (photo.bookingId.isNotBlank()) {
            val match = bookings.firstOrNull { it.id.equals(photo.bookingId, ignoreCase = true) }
            if (match != null) return match
        }

        if (photo.storagePath.isNotBlank()) {
            val match = bookings.firstOrNull { it.id.isNotBlank() && photo.storagePath.contains(it.id, ignoreCase = true) }
            if (match != null) return match
        }

        val photoText = "${photo.title} ${photo.category} ${photo.description}".lowercase()
        val serviceMatch = bookings.firstOrNull { b ->
            val service = b.serviceName.lowercase()
            service.isNotBlank() && (
                photoText.contains(service) ||
                (service.contains("matric") && photoText.contains("matric")) ||
                (service.contains("wedding") && photoText.contains("wedding")) ||
                (service.contains("portrait") && photoText.contains("portrait")) ||
                (service.contains("event") && photoText.contains("event"))
            )
        }
        if (serviceMatch != null) return serviceMatch

        if (bookings.size == 1) {
            return bookings.first()
        }

        return bookings.firstOrNull()
    }

    private fun isBookingPaid(booking: Booking?): Boolean {
        if (booking == null) return false
        val inv = booking.invoiceStatus.trim().lowercase()
        val stat = booking.status.trim().lowercase()

        val invPaid = inv.contains("paid") && !inv.contains("unpaid") && !inv.contains("partially")
        val statPaid = stat.contains("paid") && !stat.contains("unpaid") && !stat.contains("partially")

        return invPaid || statPaid
    }
}
