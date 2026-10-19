package com.example.ilyphoto

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
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
        val rememberMeSwitch = view.findViewById<SwitchCompat>(R.id.rememberMeSwitch)
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
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(
                    requireContext(),
                    "Please enter a valid registered email address first.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            forgotPassword.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ServiceLocator.auth.sendPasswordReset(email)
                forgotPassword.isEnabled = true
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Password reset instructions sent to $email.",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(
                        requireContext(),
                        err.toUserMessage(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        loginButton?.setOnClickListener {
            val email = emailInput?.text?.toString()?.trim().orEmpty()
            val password = passwordInput?.text?.toString().orEmpty()
            val rememberMe = rememberMeSwitch?.isChecked ?: true

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ServiceLocator.auth.signIn(
                    email = email,
                    password = password,
                    rememberMe = rememberMe
                )
                loginButton.isEnabled = true
                result.onSuccess {
                    Toast.makeText(requireContext(), "Signed in successfully!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                }.onFailure { err ->
                    Toast.makeText(requireContext(), err.toUserMessage(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
