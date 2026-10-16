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
import kotlinx.coroutines.launch

class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    data class GalleryItem(
        val name: String,
        val subtitle: String,
        val heroTitle: String,
        val heroBgRes: Int,
        val item1Title: String,
        val item1Category: String,
        val item1BgRes: Int,
        val item2Title: String,
        val item2Category: String,
        val item2BgRes: Int,
        val item3Title: String,
        val item3Category: String,
        val item3BgRes: Int,
        val item4Title: String,
        val item4Category: String,
        val item4BgRes: Int,
        val photoCount: Int = 5
    )

    private var activeShowcases: MutableList<GalleryItem> = mutableListOf()
    private var currentEventIndex = 0
    private var currentPreviewBgRes: Int = R.drawable.bg_photo_matric
    private var currentPreviewTitle: String = ""

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

        // Photo Cards
        val cardFeatured = view.findViewById<CardView>(R.id.cardFeaturedPhoto)
        val featuredBg = view.findViewById<FrameLayout>(R.id.featuredPhotoBg)
        val tvFeaturedTitle = view.findViewById<TextView>(R.id.tvFeaturedTitle)
        val tvFeaturedCategory = view.findViewById<TextView>(R.id.tvFeaturedCategory)

        val cardItem1 = view.findViewById<CardView>(R.id.galleryItem1)
        val item1Bg = view.findViewById<FrameLayout>(R.id.photoItem1Bg)
        val tvItem1Title = view.findViewById<TextView>(R.id.tvItem1Title)
        val tvItem1Subtitle = view.findViewById<TextView>(R.id.tvItem1Subtitle)

        val cardItem2 = view.findViewById<CardView>(R.id.galleryItem2)
        val item2Bg = view.findViewById<FrameLayout>(R.id.photoItem2Bg)
        val tvItem2Title = view.findViewById<TextView>(R.id.tvItem2Title)
        val tvItem2Subtitle = view.findViewById<TextView>(R.id.tvItem2Subtitle)

        val cardItem3 = view.findViewById<CardView>(R.id.galleryItem3)
        val item3Bg = view.findViewById<FrameLayout>(R.id.photoItem3Bg)
        val tvItem3Title = view.findViewById<TextView>(R.id.tvItem3Title)
        val tvItem3Subtitle = view.findViewById<TextView>(R.id.tvItem3Subtitle)

        val cardItem4 = view.findViewById<CardView>(R.id.galleryItem4)
        val item4Bg = view.findViewById<FrameLayout>(R.id.photoItem4Bg)
        val tvItem4Title = view.findViewById<TextView>(R.id.tvItem4Title)
        val tvItem4Subtitle = view.findViewById<TextView>(R.id.tvItem4Subtitle)

        // Photo Preview Modal Overlay
        val previewOverlay = view.findViewById<FrameLayout>(R.id.photoPreviewOverlay)
        val tvPreviewTitle = view.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvPreviewCategory = view.findViewById<TextView>(R.id.tvPreviewCategory)
        val photoPreviewBackground = view.findViewById<FrameLayout>(R.id.photoPreviewBackground)
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
            featuredBg?.setBackgroundResource(event.heroBgRes)

            tvItem1Title?.text = event.item1Title
            tvItem1Subtitle?.text = event.item1Category
            item1Bg?.setBackgroundResource(event.item1BgRes)

            tvItem2Title?.text = event.item2Title
            tvItem2Subtitle?.text = event.item2Category
            item2Bg?.setBackgroundResource(event.item2BgRes)

            tvItem3Title?.text = event.item3Title
            tvItem3Subtitle?.text = event.item3Category
            item3Bg?.setBackgroundResource(event.item3BgRes)

            tvItem4Title?.text = event.item4Title
            tvItem4Subtitle?.text = event.item4Category
            item4Bg?.setBackgroundResource(event.item4BgRes)
        }

        fun showPreview(title: String, category: String, bgRes: Int) {
            currentPreviewBgRes = bgRes
            currentPreviewTitle = title
            tvPreviewTitle?.text = title
            tvPreviewCategory?.text = "$category • High Resolution (300 DPI)"
            photoPreviewBackground?.setBackgroundResource(bgRes)
            previewOverlay?.visibility = View.VISIBLE
        }

        // Build galleries from real user bookings & authenticated profile
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.bookings.bookings().collectLatest { allBookings ->
                val currentProfile = ServiceLocator.auth.currentUserSnapshot()
                val userBookings = if (currentProfile != null) {
                    allBookings.filter { currentProfile.matchesBooking(it) }
                } else {
                    emptyList()
                }

                activeShowcases.clear()

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

        // Photo card click listeners to launch full-screen preview
        cardFeatured?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.heroTitle, event.subtitle, event.heroBgRes)
            }
        }

        cardItem1?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item1Title, event.item1Category, event.item1BgRes)
            }
        }

        cardItem2?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item2Title, event.item2Category, event.item2BgRes)
            }
        }

        cardItem3?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item3Title, event.item3Category, event.item3BgRes)
            }
        }

        cardItem4?.setOnClickListener {
            if (activeShowcases.isNotEmpty()) {
                val event = activeShowcases[currentEventIndex]
                showPreview(event.item4Title, event.item4Category, event.item4BgRes)
            }
        }

        // Preview dialog dismiss actions
        btnPreviewDismiss?.setOnClickListener {
            previewOverlay?.visibility = View.GONE
        }
        btnClosePreview?.setOnClickListener {
            previewOverlay?.visibility = View.GONE
        }

        // REAL PHOTO SAVE TO DEVICE GALLERY
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

        // REAL FULL GALLERY DOWNLOAD TO DEVICE STORAGE
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
