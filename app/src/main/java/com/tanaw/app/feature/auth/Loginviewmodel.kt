package com.tanaw.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.AuthResult
import com.tanaw.app.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        when {
            email.isBlank() && password.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Please enter your email and password.") }
                return
            }
            email.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Email address is required.") }
                return
            }
            password.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Password is required.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            userRole = result.role
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid Credentials") }
                }
            }
        }
    }

    fun consumeLoginSuccess() {
        _uiState.update { it.copy(isLoginSuccessful = false) }
    }
}
