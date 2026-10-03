package com.tanaw.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.AuthResult
import com.tanaw.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = userRepository.getUserProfile()
            result.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userProfile = profile
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to prepare database connection"
                    )
                }
            }
        }
    }

    fun updateProfileField(fieldKey: String, newValue: String) {
        val currentProfile = _uiState.value.userProfile ?: return
        val updatedProfile = when (fieldKey) {
            "fullName" -> {
                val trimmed = newValue.trim()
                val parts = trimmed.split(" ")
                val first = parts.firstOrNull() ?: ""
                val last = parts.drop(1).joinToString(" ")
                currentProfile.copy(fullName = trimmed, firstName = first, lastName = last)
            }
            "email" -> currentProfile.copy(email = newValue.trim())
            "contact" -> currentProfile.copy(phone = newValue.trim())
            "address" -> currentProfile.copy(address = newValue.trim(), city = newValue.trim().substringBefore(","))
            else -> currentProfile
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            val result = userRepository.updateUserProfile(updatedProfile)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        userProfile = updatedProfile,
                        updateSuccessMessage = "Profile updated successfully"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        errorMessage = error.message ?: "Failed to update profile"
                    )
                }
            }
        }
    }

    private fun formatPhoneToE164(phone: String): String {
        val cleaned = phone.trim().replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("09") && cleaned.length == 11 -> "+63" + cleaned.substring(1)
            cleaned.startsWith("9") && cleaned.length == 10 -> "+63" + cleaned
            cleaned.startsWith("+63") -> cleaned
            else -> cleaned
        }
    }

    fun sendPhoneVerificationOtp(onResult: (Boolean, String, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            val profileResult = userRepository.getUserProfile()
            val rawPhone = profileResult.getOrNull()?.phone?.trim()
                ?: _uiState.value.userProfile?.phone?.trim()
                ?: ""

            if (rawPhone.isBlank()) {
                _uiState.update { it.copy(isUpdating = false) }
                onResult(false, "", "No phone number found on your customer profile. Please set a contact number first.")
                return@launch
            }

            val formattedPhone = formatPhoneToE164(rawPhone)

            when (val result = authRepository.resendPhoneOtp(formattedPhone)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isUpdating = false) }
                    onResult(true, rawPhone, "Verification SMS code sent to $rawPhone.")
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isUpdating = false, errorMessage = result.message) }
                    onResult(false, rawPhone, result.message)
                }
            }
        }
    }

    fun sendEmailVerificationOtp(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            when (val result = authRepository.resendEmailOtp(email)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isUpdating = false) }
                    onResult(true, "Verification code sent to $email.")
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isUpdating = false, errorMessage = result.message) }
                    onResult(false, result.message)
                }
            }
        }
    }

    fun verifyOtp(method: String, destination: String, token: String, onResult: (Boolean, String) -> Unit) {
        if (token.length != 6) {
            onResult(false, "Please enter a valid 6-digit code.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            val result = if (method.equals("PHONE", ignoreCase = true)) {
                val formattedPhone = formatPhoneToE164(destination)
                authRepository.verifyPhoneOtp(formattedPhone, token)
            } else {
                authRepository.verifyEmailOtp(destination, token)
            }

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isUpdating = false) }
                    onResult(true, "Identity verified successfully.")
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isUpdating = false, errorMessage = result.message) }
                    onResult(false, result.message)
                }
            }
        }
    }

    fun changePassword(newPassword: String, onResult: (Boolean, String) -> Unit) {
        if (newPassword.length < 8) {
            onResult(false, "Password must be at least 8 characters long.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
            when (val result = authRepository.changePassword(newPassword)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            updateSuccessMessage = "Password changed successfully!"
                        )
                    }
                    onResult(true, "Password changed successfully!")
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorMessage = result.message
                        )
                    }
                    onResult(false, result.message)
                }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, updateSuccessMessage = null) }
    }
}
