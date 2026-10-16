package com.example.ilyphoto

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.ilyphoto.data.ServiceLocator
import com.example.ilyphoto.data.toUserMessage
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailInput = view.findViewById<EditText>(R.id.emailInput)
        val passwordInput = view.findViewById<EditText>(R.id.passwordInput)
        val loginButton = view.findViewById<TextView>(R.id.loginButton)
        val registerTab = view.findViewById<TextView>(R.id.registerTab)
        val signUpNow = view.findViewById<TextView>(R.id.signUpNow)
        val forgotPassword = view.findViewById<TextView>(R.id.forgotPassword)

        registerTab?.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        signUpNow?.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        forgotPassword?.setOnClickListener {
            val email = emailInput?.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ServiceLocator.auth.sendPasswordReset(email)
                result.onSuccess {
                    Toast.makeText(requireContext(), "Password reset link sent to $email", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(requireContext(), err.toUserMessage(), Toast.LENGTH_SHORT).show()
                }
            }
        }

        loginButton?.setOnClickListener {
            val email = emailInput?.text?.toString()?.trim().orEmpty()
            val password = passwordInput?.text?.toString().orEmpty()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ServiceLocator.auth.signIn(email, password)
                loginButton.isEnabled = true
                result.onSuccess {
                    Toast.makeText(requireContext(), "Signed in successfully!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                }.onFailure { err ->
                    Toast.makeText(requireContext(), err.toUserMessage(), Toast.LENGTH_LONG).show()
                    // If credentials failed or offline, allow seamless transition if user wants to explore
                }
            }
        }
    }
}
