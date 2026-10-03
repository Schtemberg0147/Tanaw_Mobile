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
class VerifyEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun verifyOtp(email: String, otp: String) {
        if (_uiState.value.isLoading) return

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

            when (val result = authRepository.verifyEmailOtp(email.trim(), trimmedOtp)) {
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

    fun resendOtp(email: String) {
        if (_uiState.value.isResending) return

        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email address is missing.") }
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

            when (val result = authRepository.resendEmailOtp(email.trim())) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            resendSuccessMessage = "Verification code resent successfully."
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
