package com.example.ilyphoto

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import java.util.Calendar
import java.util.Locale

class RegisterFragment : Fragment(R.layout.fragment_register) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val firstNameInput = view.findViewById<EditText>(R.id.firstNameInput)
        val surnameInput = view.findViewById<EditText>(R.id.surnameInput)
        val emailInput = view.findViewById<EditText>(R.id.registerEmailInput)
        val passwordInput = view.findViewById<EditText>(R.id.registerPasswordInput)
        val tvPasswordStrength = view.findViewById<TextView>(R.id.tvPasswordStrength)
        val dateOfBirthInput = view.findViewById<EditText>(R.id.dateOfBirthInput)
        val signUpButton = view.findViewById<TextView>(R.id.signUpButton)
        val loginTab = view.findViewById<TextView>(R.id.loginTab)
        val backToLogin = view.findViewById<TextView>(R.id.backToLogin)

        // 1. Navigation tabs
        loginTab?.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        backToLogin?.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        // 2. Interactive Date Picker for Date of Birth
        val calendar = Calendar.getInstance()
        val datePickerListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val formatted = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            dateOfBirthInput?.setText(formatted)
        }

        val openDatePicker = View.OnClickListener {
            val dpd = DatePickerDialog(
                requireContext(),
                datePickerListener,
                calendar.get(Calendar.YEAR) - 18,
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            dpd.datePicker.maxDate = System.currentTimeMillis() // Born in the past
            dpd.show()
        }

        dateOfBirthInput?.setOnClickListener(openDatePicker)

        // 3. Password Validation & Strength Standard
        fun evaluatePasswordStrength(password: String): Int {
            if (password.isEmpty()) return 0
            var score = 0
            if (password.length >= 8) score++
            if (password.any { it.isUpperCase() }) score++
            if (password.any { it.isLowerCase() }) score++
            if (password.any { it.isDigit() }) score++
            if (password.any { !it.isLetterOrDigit() }) score++
            return score
        }

        passwordInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pass = s?.toString().orEmpty()
                val score = evaluatePasswordStrength(pass)

                when {
                    pass.isEmpty() -> {
                        tvPasswordStrength?.text = getString(R.string.password_weak)
                        tvPasswordStrength?.setTextColor(Color.parseColor("#FFE082"))
                    }
                    score <= 2 -> {
                        tvPasswordStrength?.text = "Weak: Need 8+ characters, mixed case, numbers & symbols"
                        tvPasswordStrength?.setTextColor(Color.parseColor("#EF5350"))
                    }
                    score in 3..4 -> {
                        tvPasswordStrength?.text = "Moderate: Add missing uppercase, number, or special character"
                        tvPasswordStrength?.setTextColor(Color.parseColor("#FFA726"))
                    }
                    else -> {
                        tvPasswordStrength?.text = "Strong Password ✓ (Meets security standards)"
                        tvPasswordStrength?.setTextColor(Color.parseColor("#66BB6A"))
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Form Submission & Validation
        signUpButton?.setOnClickListener {
            val firstName = firstNameInput?.text?.toString()?.trim().orEmpty()
            val surname = surnameInput?.text?.toString()?.trim().orEmpty()
            val email = emailInput?.text?.toString()?.trim().orEmpty()
            val password = passwordInput?.text?.toString().orEmpty()
            val dob = dateOfBirthInput?.text?.toString()?.trim().orEmpty()

            if (firstName.isEmpty() || surname.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both first name and surname", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dob.isEmpty()) {
                Toast.makeText(requireContext(), "Please select your Date of Birth", Toast.LENGTH_SHORT).show()
                dateOfBirthInput?.performClick()
                return@setOnClickListener
            }

            val strengthScore = evaluatePasswordStrength(password)
            if (password.length < 8) {
                Toast.makeText(requireContext(), "Password must be at least 8 characters long", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (strengthScore < 3) {
                Toast.makeText(
                    requireContext(),
                    "Password is too weak. Please combine uppercase, lowercase, numbers, and symbols.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val fullName = "$firstName $surname"
            signUpButton.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = ServiceLocator.auth.register(
                    displayName = fullName,
                    email = email,
                    password = password,
                    username = email.substringBefore('@'),
                    phone = "",
                    isPhotographer = false
                )
                signUpButton.isEnabled = true
                result.onSuccess {
                    IlyNotificationHelper.notifyPushEnabled(requireContext())
                    Toast.makeText(requireContext(), "Welcome to Ily Photo, $fullName!", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
                }.onFailure { err ->
                    Toast.makeText(requireContext(), err.toUserMessage(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
