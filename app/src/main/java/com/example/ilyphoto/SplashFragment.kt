package com.example.ilyphoto

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val handler = Handler(Looper.getMainLooper())

    private val navigateRunnable = Runnable {
        if (isAdded && isResumed) {
            try {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.splashFragment) {
                    val currentUser = com.example.ilyphoto.data.ServiceLocator.auth.currentUserSnapshot()
                    if (currentUser != null) {
                        navController.navigate(R.id.action_splashFragment_to_homeFragment)
                    } else {
                        navController.navigate(R.id.action_splashFragment_to_loginFragment)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        handler.postDelayed(navigateRunnable, 2000)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroyView()
    }
}
