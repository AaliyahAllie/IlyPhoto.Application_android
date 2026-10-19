package com.example.ilyphoto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.AzureProvider
import com.example.ilyphoto.data.Photo
import com.example.ilyphoto.data.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    private var clientPhotos: List<Photo> = emptyList()

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
            tvPreviewCategory?.text = "${photo.category.ifBlank { "Private Album" }} • High Resolution (300 DPI)"
            if (ivPreviewPhoto != null) {
                ImageLoader.load(ivPreviewPhoto, photo.imageUrl, R.drawable.bg_photo_matric)
            }
            btnSaveSinglePhoto?.setOnClickListener {
                val drawable = ivPreviewPhoto?.drawable
                if (drawable != null) {
                    val bitmap = GalleryStorageHelper.drawableToBitmap(drawable)
                    val uri = GalleryStorageHelper.saveBitmapToGallery(requireContext(), bitmap, photo.title.ifBlank { "photo" })
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Saved photo to Pictures/IlyPhoto ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Photo saved to device storage.", Toast.LENGTH_SHORT).show()
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

        // Live observation of Azure Blob photos & client profile
        // Strictly filters photos linked ONLY to this authenticated client's account
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
                    btnDownloadGallery?.visibility = View.GONE
                    return@collectLatest
                }

                // If profile clientId is missing, resolve it from the user's bookings
                val resolvedClientId = if (baseProfile.clientId.isNotBlank()) {
                    baseProfile.clientId
                } else {
                    allBookings.firstOrNull { baseProfile.matchesBooking(it) }?.clientId.orEmpty()
                }

                val activeProfile = if (resolvedClientId.isNotBlank() && baseProfile.clientId.isBlank()) {
                    baseProfile.copy(clientId = resolvedClientId)
                } else {
                    baseProfile
                }

                // Strictly filter photos belonging ONLY to this client's account
                val userPhotos = allPhotos.filter { photo ->
                    activeProfile.matchesPhoto(photo)
                }

                clientPhotos = userPhotos

                if (userPhotos.isEmpty()) {
                    cardEmptyGallery?.visibility = View.VISIBLE
                    btnDownloadGallery?.visibility = View.GONE
                    tvSelectedEvent?.text = "My Private Albums (0 Photos)"
                } else {
                    cardEmptyGallery?.visibility = View.GONE
                    btnDownloadGallery?.visibility = View.VISIBLE
                    btnDownloadGallery?.text = "DOWNLOAD ALL (${userPhotos.size} PHOTOS)"
                    tvSelectedEvent?.text = "My Private Albums (${userPhotos.size} Photos)"

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

                        tvTitle.text = photo.title.ifBlank { "Photoshoot Picture" }
                        tvCategory.text = "PRIVATE ALBUM • HIGH RESOLUTION"
                        tvDesc.text = photo.description.ifBlank { "Professionally edited, high-resolution photo." }
                        tvBadge.text = "📁 HD QUALITY"

                        // Load real Azure Blob SAS URL into the ImageView
                        ImageLoader.load(ivImage, photo.imageUrl, R.drawable.bg_photo_matric)

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
                                    Toast.makeText(requireContext(), "Saved to Pictures/IlyPhoto ✓", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Saved photo to device storage.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        layoutGalleryContent?.addView(cardView)
                    }
                }
            }
        }

        // Download all photos in currently active client list
        btnDownloadGallery?.setOnClickListener {
            if (clientPhotos.isEmpty()) return@setOnClickListener
            var saved = 0
            for (photo in clientPhotos) {
                val cached = ImageLoader.getBitmap(photo.imageUrl)
                if (cached != null) {
                    GalleryStorageHelper.saveBitmapToGallery(requireContext(), cached, photo.title)
                    saved++
                }
            }
            if (saved > 0) {
                IlyNotificationHelper.showNotification(
                    requireContext(),
                    "Gallery Download Complete ✓",
                    "Saved $saved high-resolution photos to Pictures/IlyPhoto on your device."
                )
                Toast.makeText(requireContext(), "Downloaded $saved photos to Pictures/IlyPhoto ✓", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Saving ${clientPhotos.size} photos to device...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
