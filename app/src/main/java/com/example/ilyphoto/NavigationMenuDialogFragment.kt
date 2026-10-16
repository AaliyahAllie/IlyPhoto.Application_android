package com.example.ilyphoto

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.navigation.Navigation
import com.example.ilyphoto.data.ServiceLocator

class NavigationMenuDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0f) // Scrim view in layout handles dimming smoothly
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrim = view.findViewById<View>(R.id.menuOutsideScrim)
        val itemProfile = view.findViewById<TextView>(R.id.menuItemProfile)
        val itemSettings = view.findViewById<TextView>(R.id.menuItemSettings)
        val itemHome = view.findViewById<TextView>(R.id.menuItemHome)
        val itemGallery = view.findViewById<TextView>(R.id.menuItemGallery)
        val itemBookings = view.findViewById<TextView>(R.id.menuItemBookings)
        val itemInvoices = view.findViewById<TextView>(R.id.menuItemInvoices)
        val itemPackages = view.findViewById<TextView>(R.id.menuItemPackages)
        val btnLogOut = view.findViewById<TextView>(R.id.btnMenuLogOut)

        scrim?.setOnClickListener {
            dismissAllowingStateLoss()
        }

        itemProfile?.setOnClickListener {
            navigateTo(R.id.action_global_to_profileFragment)
        }

        itemSettings?.setOnClickListener {
            navigateTo(R.id.action_global_to_settingsFragment)
        }

        itemHome?.setOnClickListener {
            navigateTo(R.id.action_global_to_homeFragment)
        }

        itemGallery?.setOnClickListener {
            navigateTo(R.id.action_global_to_galleryFragment)
        }

        itemBookings?.setOnClickListener {
            navigateTo(R.id.action_global_to_myBookingFragment)
        }

        itemInvoices?.setOnClickListener {
            navigateTo(R.id.action_global_to_invoiceFragment)
        }

        itemPackages?.setOnClickListener {
            navigateTo(R.id.action_global_to_packagesFragment)
        }

        btnLogOut?.setOnClickListener {
            ServiceLocator.auth.signOut()
            Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()
            navigateTo(R.id.action_global_to_loginFragment)
        }
    }

    private fun navigateTo(actionId: Int) {
        val act = activity
        dismissAllowingStateLoss()
        if (act != null) {
            try {
                val navController = Navigation.findNavController(act, R.id.nav_host_fragment)
                navController.navigate(actionId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
