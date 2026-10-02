package com.tanaw.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VerifyPhoneViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyPhoneUiState())
    val uiState: StateFlow<VerifyPhoneUiState> = _uiState.asStateFlow()

    fun verifyOtp(phone: String, otp: String) {
        val trimmedOtp = otp.trim()
        if (trimmedOtp.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 6-digit verification code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    resendSuccessMessage = null
                )
            }

            when (val result = authRepository.verifyPhoneOtp(phone.trim(), trimmedOtp)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun resendOtp(phone: String) {
        if (_uiState.value.isResending) return

        if (phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Phone number is missing.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResending = true,
                    errorMessage = null,
                    resendSuccessMessage = null
                )
            }

            when (val result = authRepository.resendPhoneOtp(phone.trim())) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            resendSuccessMessage = "Verification code resent via SMS."
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun consumeVerificationSuccess() {
        _uiState.update { it.copy(isVerified = false) }
    }
}
