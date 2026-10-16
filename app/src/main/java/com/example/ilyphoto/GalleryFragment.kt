package com.example.ilyphoto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.Booking
import com.example.ilyphoto.data.Photo
import com.example.ilyphoto.data.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    data class GalleryItem(
        val name: String,
        val subtitle: String,
        val heroTitle: String,
        val heroBgRes: Int = R.drawable.bg_photo_portrait,
        val heroImageUrl: String? = null,
        val item1Title: String,
        val item1Category: String,
        val item1BgRes: Int = R.drawable.bg_photo_wedding,
        val item1ImageUrl: String? = null,
        val item2Title: String,
        val item2Category: String,
        val item2BgRes: Int = R.drawable.bg_photo_portrait,
        val item2ImageUrl: String? = null,
        val item3Title: String,
        val item3Category: String,
        val item3BgRes: Int = R.drawable.bg_photo_event,
        val item3ImageUrl: String? = null,
        val item4Title: String,
        val item4Category: String,
        val item4BgRes: Int = R.drawable.bg_photo_matric,
        val item4ImageUrl: String? = null,
        val photoCount: Int = 5
    )

    private var activeShowcases: MutableList<GalleryItem> = mutableListOf()
    private var currentEventIndex = 0
    private var currentPreviewBgRes: Int = R.drawable.bg_photo_matric
    private var currentPreviewTitle: String = ""
    private var currentPreviewUrl: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        val btnEventDropdown = view.findViewById<LinearLayout>(R.id.btnEventNameDropdown)
        val tvSelectedEvent = view.findViewById<TextView>(R.id.tvSelectedEventName)
        val btnDownload = view.findViewById<TextView>(R.id.btnDownloadGallery)

        val cardEmptyGallery = view.findViewById<View>(R.id.cardEmptyGallery)
        val layoutGalleryContent = view.findViewById<View>(R.id.layoutGalleryContent)
        val btnEmptyGalleryBook = view.findViewById<View>(R.id.btnEmptyGalleryBook)

        // Photo Cards & ImageViews
        val cardFeatured = view.findViewById<CardView>(R.id.cardFeaturedPhoto)
        val featuredBg = view.findViewById<FrameLayout>(R.id.featuredPhotoBg)
        val ivFeatured = view.findViewById<ImageView>(R.id.ivFeatured)
        val tvFeaturedTitle = view.findViewById<TextView>(R.id.tvFeaturedTitle)
        val tvFeaturedCategory = view.findViewById<TextView>(R.id.tvFeaturedCategory)

        val cardItem1 = view.findViewById<CardView>(R.id.galleryItem1)
        val item1Bg = view.findViewById<FrameLayout>(R.id.photoItem1Bg)
        val ivItem1 = view.findViewById<ImageView>(R.id.ivItem1)
        val tvItem1Title = view.findViewById<TextView>(R.id.tvItem1Title)
        val tvItem1Subtitle = view.findViewById<TextView>(R.id.tvItem1Subtitle)

        val cardItem2 = view.findViewById<CardView>(R.id.galleryItem2)
        val item2Bg = view.findViewById<FrameLayout>(R.id.photoItem2Bg)
        val ivItem2 = view.findViewById<ImageView>(R.id.ivItem2)
        val tvItem2Title = view.findViewById<TextView>(R.id.tvItem2Title)
        val tvItem2Subtitle = view.findViewById<TextView>(R.id.tvItem2Subtitle)

        val cardItem3 = view.findViewById<CardView>(R.id.galleryItem3)
        val item3Bg = view.findViewById<FrameLayout>(R.id.photoItem3Bg)
        val ivItem3 = view.findViewById<ImageView>(R.id.ivItem3)
        val tvItem3Title = view.findViewById<TextView>(R.id.tvItem3Title)
        val tvItem3Subtitle = view.findViewById<TextView>(R.id.tvItem3Subtitle)

        val cardItem4 = view.findViewById<CardView>(R.id.galleryItem4)
        val item4Bg = view.findViewById<FrameLayout>(R.id.photoItem4Bg)
        val ivItem4 = view.findViewById<ImageView>(R.id.ivItem4)
        val tvItem4Title = view.findViewById<TextView>(R.id.tvItem4Title)
        val tvItem4Subtitle = view.findViewById<TextView>(R.id.tvItem4Subtitle)

        // Photo Preview Modal Overlay
        val previewOverlay = view.findViewById<FrameLayout>(R.id.photoPreviewOverlay)
        val tvPreviewTitle = view.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvPreviewCategory = view.findViewById<TextView>(R.id.tvPreviewCategory)
        val photoPreviewBackground = view.findViewById<FrameLayout>(R.id.photoPreviewBackground)
        val ivPreviewPhoto = view.findViewById<ImageView>(R.id.ivPreviewPhoto)
        val btnPreviewDismiss = view.findViewById<ImageView>(R.id.btnPreviewDismiss)
        val btnClosePreview = view.findViewById<TextView>(R.id.btnClosePreviewDialog)
        val btnSaveSinglePhoto = view.findViewById<TextView>(R.id.btnDownloadSinglePhoto)

        // Navigation
        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        fun updateEventDisplay() {
            if (activeShowcases.isEmpty()) {
                cardEmptyGallery?.visibility = View.VISIBLE
                layoutGalleryContent?.visibility = View.GONE
                btnDownload?.visibility = View.GONE
                tvSelectedEvent?.text = getString(R.string.no_gallery_photos)
                return
            }

            cardEmptyGallery?.visibility = View.GONE
            layoutGalleryContent?.visibility = View.VISIBLE
            btnDownload?.visibility = View.VISIBLE

            if (currentEventIndex >= activeShowcases.size) {
                currentEventIndex = 0
            }

            val event = activeShowcases[currentEventIndex]
            tvSelectedEvent?.text = event.name

            tvFeaturedTitle?.text = event.heroTitle
            tvFeaturedCategory?.text = "FEATURED • ${event.subtitle.uppercase()}"
            if (!event.heroImageUrl.isNullOrBlank()) {
                ivFeatured?.let { ImageLoader.load(it, event.heroImageUrl, event.heroBgRes) }
            } else {
                ivFeatured?.setImageResource(event.heroBgRes)
            }

            tvItem1Title?.text = event.item1Title
            tvItem1Subtitle?.text = event.item1Category
            if (!event.item1ImageUrl.isNullOrBlank()) {
                ivItem1?.let { ImageLoader.load(it, event.item1ImageUrl, event.item1BgRes) }
            } else {
                ivItem1?.setImageResource(event.item1BgRes)
            }

            tvItem2Title?.text = event.item2Title
            tvItem2Subtitle?.text = event.item2Category
            if (!event.item2ImageUrl.isNullOrBlank()) {
                ivItem2?.let { ImageLoader.load(it, event.item2ImageUrl, event.item2BgRes) }
            } else {
                ivItem2?.setImageResource(event.item2BgRes)
            }

            tvItem3Title?.text = event.item3Title
            tvItem3Subtitle?.text = event.item3Category
            if (!event.item3ImageUrl.isNullOrBlank()) {
                ivItem3?.let { ImageLoader.load(it, event.item3ImageUrl, event.item3BgRes) }
            } else {
                ivItem3?.setImageResource(event.item3BgRes)
            }

            tvItem4Title?.text = event.item4Title
            tvItem4Subtitle?.text = event.item4Category
            if (!event.item4ImageUrl.isNullOrBlank()) {
                ivItem4?.let { ImageLoader.load(it, event.item4ImageUrl, event.item4BgRes) }
            } else {
                ivItem4?.setImageResource(event.item4BgRes)
            }
        }

        fun showPreview(title: String, category: String, bgRes: Int, imageUrl: String? = null) {
            currentPreviewBgRes = bgRes
            currentPreviewTitle = title
            currentPreviewUrl = imageUrl
            tvPreviewTitle?.text = title
            tvPreviewCategory?.text = "$category • High Resolution (300 DPI)"
            if (!imageUrl.isNullOrBlank() && ivPreviewPhoto != null) {
                ImageLoader.load(ivPreviewPhoto, imageUrl, bgRes)
            } else {
                ivPreviewPhoto?.setImageResource(bgRes)
            }
            previewOverlay?.visibility = View.VISIBLE
        }

        // Live observation of real Azure Blob Storage photos & Bookings
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                ServiceLocator.auth.currentUser,
                ServiceLocator.photos.photos(),
                ServiceLocator.bookings.bookings()
            ) { currentProfile, allPhotos, allBookings ->
                Triple(currentProfile, allPhotos, allBookings)
            }.collectLatest { (currentProfile, allPhotos, allBookings) ->
                activeShowcases.clear()

                // 1. First Priority: Actual Photos uploaded to Azure Blob Storage for this client (matches Website)
                val clientPhotos = if (currentProfile != null) {
                    allPhotos.filter {
                        it.clientId.equals(currentProfile.clientId, ignoreCase = true) ||
                        it.clientId.equals(currentProfile.uid, ignoreCase = true) ||
                        (currentProfile.email.isNotBlank() && it.clientId.equals(currentProfile.email, ignoreCase = true))
                    }
                } else {
                    emptyList()
                }

                if (clientPhotos.isNotEmpty()) {
                    val p0 = clientPhotos[0]
                    val p1 = clientPhotos.getOrNull(1) ?: p0
                    val p2 = clientPhotos.getOrNull(2) ?: p0
                    val p3 = clientPhotos.getOrNull(3) ?: p1
                    val p4 = clientPhotos.getOrNull(4) ?: p0

                    activeShowcases.add(
                        GalleryItem(
                            name = "${p0.title} (${clientPhotos.size} Photos)",
                            subtitle = "Private Client Gallery",
                            heroTitle = p0.title.ifBlank { "Client Gallery" },
                            heroBgRes = R.drawable.bg_photo_portrait,
                            heroImageUrl = p0.imageUrl,
                            item1Title = p1.title.ifBlank { "Photo 2" },
                            item1Category = "High Resolution",
                            item1BgRes = R.drawable.bg_photo_wedding,
                            item1ImageUrl = p1.imageUrl,
                            item2Title = p2.title.ifBlank { "Photo 3" },
                            item2Category = "High Resolution",
                            item2BgRes = R.drawable.bg_photo_matric,
                            item2ImageUrl = p2.imageUrl,
                            item3Title = p3.title.ifBlank { "Photo 4" },
                            item3Category = "High Resolution",
                            item3BgRes = R.drawable.bg_photo_event,
                            item3ImageUrl = p3.imageUrl,
                            item4Title = p4.title.ifBlank { "Photo 5" },
                            item4Category = "High Resolution",
                            item4BgRes = R.drawable.bg_photo_portrait,
                            item4ImageUrl = p4.imageUrl,
                            photoCount = clientPhotos.size
                        )
                    )
                }

                // 2. Also include scheduled booking showcases for complete portfolio access
                val userBookings = if (currentProfile != null) {
                    allBookings.filter { currentProfile.matchesBooking(it) }
                } else {
                    emptyList()
                }

                for (booking in userBookings) {
                    val serviceLower = booking.serviceName.lowercase()
                    when {
                        serviceLower.contains("matric") -> {
                            activeShowcases.add(
                                GalleryItem(
                                    name = "${booking.serviceName} (${booking.eventDate})",
                                    subtitle = "Matric Ball Session",
                                    heroTitle = "Golden Hour & Red Carpet • ${booking.eventDate}",
                                    heroBgRes = R.drawable.bg_photo_matric,
                                    item1Title = "Arrival & Formal Portraits",
                                    item1Category = "Matric Ball",
                                    item1BgRes = R.drawable.bg_photo_matric,
                                    item2Title = "Sunset Coastline Walk",
                                    item2Category = "Matric Ball",
                                    item2BgRes = R.drawable.bg_photo_portrait,
                                    item3Title = "Evening Ball Dress Highlights",
                                    item3Category = "Matric Ball",
                                    item3BgRes = R.drawable.bg_photo_wedding,
                                    item4Title = "Celebration with Friends",
                                    item4Category = "Matric Ball",
                                    item4BgRes = R.drawable.bg_photo_matric
                                )
                            )
                        }
                        serviceLower.contains("wedding") -> {
                            activeShowcases.add(
                                GalleryItem(
                                    name = "${booking.serviceName} (${booking.eventDate})",
                                    subtitle = "Wedding Photoshoot",
                                    heroTitle = "Ceremony & Golden Hour Sunset • ${booking.eventDate}",
                                    heroBgRes = R.drawable.bg_photo_wedding,
                                    item1Title = "Bride & Groom Vows",
                                    item1Category = "Weddings",
                                    item1BgRes = R.drawable.bg_photo_wedding,
                                    item2Title = "Bridal Party & Family",
                                    item2Category = "Weddings",
                                    item2BgRes = R.drawable.bg_photo_portrait,
                                    item3Title = "Sunset Walkway Rings",
                                    item3Category = "Weddings",
                                    item3BgRes = R.drawable.bg_photo_event,
                                    item4Title = "Evening Reception Dance",
                                    item4Category = "Weddings",
                                    item4BgRes = R.drawable.bg_photo_wedding
                                )
                            )
                        }
                        else -> {
                            activeShowcases.add(
                                GalleryItem(
                                    name = "${booking.serviceName} (${booking.eventDate})",
                                    subtitle = "Photoshoot Session",
                                    heroTitle = "Editorial Studio & Outdoor Session • ${booking.eventDate}",
                                    heroBgRes = R.drawable.bg_photo_portrait,
                                    item1Title = "Dramatic Studio Headshot",
                                    item1Category = "Portraits",
                                    item1BgRes = R.drawable.bg_photo_portrait,
                                    item2Title = "Natural Light Session",
                                    item2Category = "Portraits",
                                    item2BgRes = R.drawable.bg_photo_matric,
                                    item3Title = "Black & White Classic Profile",
                                    item3Category = "Portraits",
                                    item3BgRes = R.drawable.bg_photo_portrait,
                                    item4Title = "Outdoor Lifestyle Look",
                                    item4Category = "Portraits",
                                    item4BgRes = R.drawable.bg_photo_event
                                )
                            )
                        }
                    }
                }

                updateEventDisplay()
            }
        }

        // Event Switcher Dropdown
        btnEventDropdown?.setOnClickListener {
            if (activeShowcases.isEmpty()) {
                Toast.makeText(requireContext(), "No photo galleries available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val eventTitles = activeShowcases.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Gallery Showcase")
                .setItems(eventTitles) { _, which ->
                    currentEventIndex = which
                    updateEventDisplay()
                    Toast.makeText(requireContext(), "Loaded ${activeShowcases[which].name}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        btnEmptyGalleryBook?.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_bookingFragment)
        }

        // Photo card click listeners to launch full-screen preview with real Azure Blob image
        cardFeatured?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.heroTitle, event.subtitle, event.heroBgRes, event.heroImageUrl)
            }
        }

        cardItem1?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item1Title, event.item1Category, event.item1BgRes, event.item1ImageUrl)
            }
        }

        cardItem2?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item2Title, event.item2Category, event.item2BgRes, event.item2ImageUrl)
            }
        }

        cardItem3?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item3Title, event.item3Category, event.item3BgRes, event.item3ImageUrl)
            }
        }

        cardItem4?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item4Title, event.item4Category, event.item4BgRes, event.item4ImageUrl)
            }
        }

        // Preview dialog dismiss actions
        btnPreviewDismiss?.setOnClickListener {
            previewOverlay?.visibility = View.GONE
        }
        btnClosePreview?.setOnClickListener {
            previewOverlay?.visibility = View.GONE
        }

        // Save photo to device
        btnSaveSinglePhoto?.setOnClickListener {
            val uri = GalleryStorageHelper.saveDrawableToGallery(
                requireContext(),
                currentPreviewBgRes,
                currentPreviewTitle
            )
            if (uri != null) {
                Toast.makeText(
                    requireContext(),
                    "Saved '$currentPreviewTitle' to Pictures/IlyPhoto in your Gallery!",
                    Toast.LENGTH_LONG
                ).show()
                IlyNotificationHelper.showNotification(
                    requireContext(),
                    "Photo Saved to Device ✓",
                    "Saved '$currentPreviewTitle' to your Pictures/IlyPhoto gallery folder."
                )
            } else {
                Toast.makeText(requireContext(), "Saved photo to device storage.", Toast.LENGTH_SHORT).show()
            }
            previewOverlay?.visibility = View.GONE
        }

        // Download gallery to device storage
        btnDownload?.setOnClickListener {
            if (activeShowcases.isEmpty()) return@setOnClickListener
            val event = activeShowcases[currentEventIndex]
            val photosToSave = listOf(
                event.heroBgRes to "${event.subtitle}_Featured",
                event.item1BgRes to "${event.subtitle}_1",
                event.item2BgRes to "${event.subtitle}_2",
                event.item3BgRes to "${event.subtitle}_3",
                event.item4BgRes to "${event.subtitle}_4"
            )
            val savedCount = GalleryStorageHelper.saveMultipleDrawables(requireContext(), photosToSave)
            Toast.makeText(
                requireContext(),
                "Saved $savedCount HD photos for '${event.name}' to Pictures/IlyPhoto on your device!",
                Toast.LENGTH_LONG
            ).show()
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
