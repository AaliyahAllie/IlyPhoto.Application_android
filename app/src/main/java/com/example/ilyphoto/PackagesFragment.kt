package com.example.ilyphoto

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PackagesFragment : Fragment(R.layout.fragment_packages) {

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

        val btnBookMatric = view.findViewById<TextView>(R.id.btnBookMatricBalls)
        val btnBookWeddings = view.findViewById<TextView>(R.id.btnBookWeddings)
        val btnBookEvents = view.findViewById<TextView>(R.id.btnBookEvents)
        val btnBookCommunityOutreach = view.findViewById<TextView>(R.id.btnBookCommunityOutreach)
        val btnViewBookings = view.findViewById<TextView>(R.id.btnPackagesViewBookings)

        val navHome = view.findViewById<FrameLayout>(R.id.navHome)
        val navSettings = view.findViewById<FrameLayout>(R.id.navSettings)
        val navMenu = view.findViewById<FrameLayout>(R.id.navMenu)

        fun navigateWithPackage(packageName: String, toastMessage: String) {
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show()
            val bundle = Bundle().apply {
                putString("selectedPackageName", packageName)
            }
            findNavController().navigate(R.id.action_packagesFragment_to_bookingFragment, bundle)
        }

        btnBookMatric?.setOnClickListener {
            navigateWithPackage("Matric Ball", "Selected: Matric Ball (R400 / 2h)")
        }

        btnBookWeddings?.setOnClickListener {
            navigateWithPackage("Weddings", "Selected: Weddings (From R1200 / 6h)")
        }

        btnBookEvents?.setOnClickListener {
            navigateWithPackage("Events", "Selected: Events (R400 / 2h)")
        }

        btnBookCommunityOutreach?.setOnClickListener {
            navigateWithPackage(
                "Community / NGO Outreach",
                "Selected: Community / NGO Outreach (R200 / 2h Subsidised Rate)"
            )
        }

        btnViewBookings?.setOnClickListener {
            findNavController().navigate(R.id.action_packagesFragment_to_myBookingFragment)
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
