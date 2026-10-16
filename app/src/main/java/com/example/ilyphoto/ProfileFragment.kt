package com.example.ilyphoto

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.ServiceLocator
import com.example.ilyphoto.data.UserProfile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var currentProfile: UserProfile? = null
    private var ivProfileAvatar: ImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val prof = currentProfile ?: ServiceLocator.auth.currentUserSnapshot()
            if (prof != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    ProfileAvatarHelper.saveAndUploadAvatar(requireContext(), uri, prof) { success ->
                        if (success) {
                            ProfileAvatarHelper.loadAvatar(requireContext(), ivProfileAvatar, prof)
                            Toast.makeText(requireContext(), "Profile picture saved to your account in database!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Could not save profile picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                ivProfileAvatar?.setImageURI(uri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etFirstName = view.findViewById<EditText>(R.id.etProfileFirstName)
        val etLastName = view.findViewById<EditText>(R.id.etProfileLastName)
        val etEmail = view.findViewById<EditText>(R.id.etProfileEmail)
        val etPhone = view.findViewById<EditText>(R.id.etProfilePhone)

        val profileAvatar = view.findViewById<ImageView>(R.id.profileAvatar)
        val btnEditAvatar = view.findViewById<ImageView>(R.id.btnEditAvatar)
        val btnSaveProfile = view.findViewById<TextView>(R.id.btnSaveProfile)
        val btnLogOut = view.findViewById<TextView>(R.id.btnProfileLogOut)

        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        ivProfileAvatar = profileAvatar

        // Live observation of user profile from Azure Cloud Database (including client-linked photoUrl)
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.auth.currentUser.collectLatest { profile ->
                if (profile != null) {
                    currentProfile = profile
                    ProfileAvatarHelper.loadAvatar(requireContext(), profileAvatar, profile)

                    val names = profile.displayName.split(" ", limit = 2)
                    if (names.isNotEmpty() && names[0].isNotBlank()) {
                        etFirstName?.setText(names[0])
                    }
                    if (names.size > 1 && names[1].isNotBlank()) {
                        etLastName?.setText(names[1])
                    }
                    if (profile.email.isNotBlank()) {
                        etEmail?.setText(profile.email)
                    }
                    if (profile.phone.isNotBlank()) {
                        etPhone?.setText(profile.phone)
                    }
                }
            }
        }

        // Pick photo on avatar click or edit button click
        val avatarClickListener = View.OnClickListener {
            pickImageLauncher.launch("image/*")
        }
        profileAvatar?.setOnClickListener(avatarClickListener)
        btnEditAvatar?.setOnClickListener(avatarClickListener)

        btnSaveProfile?.setOnClickListener {
            val prof = currentProfile
            val updatedName = "${etFirstName?.text?.toString()?.trim()} ${etLastName?.text?.toString()?.trim()}".trim()
            val updatedPhone = etPhone?.text?.toString()?.trim().orEmpty()

            if (prof != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    ServiceLocator.auth.updateProfileDetails(
                        current = prof,
                        displayName = updatedName.ifBlank { prof.displayName },
                        username = prof.username,
                        phone = updatedPhone,
                        address = prof.address,
                        role = prof.role
                    )
                    Toast.makeText(requireContext(), "Profile changes saved successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Profile changes saved locally!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogOut?.setOnClickListener {
            ServiceLocator.auth.signOut()
            Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_global_to_loginFragment)
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
