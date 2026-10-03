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
class CreateAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    fun signUp(
        firstName: String,
        lastName: String,
        contactNumber: String,
        email: String,
        password: String
    ) {
        val trimmedEmail = email.trim()
        val trimmedPhone = contactNumber.trim()
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        when {
            firstName.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "First name is required.") }
                return
            }
            lastName.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Last name is required.") }
                return
            }
            contactNumber.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Contact number is required.") }
                return
            }
            email.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Email address is required.") }
                return
            }
            !emailRegex.matches(trimmedEmail) -> {
                _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
                return
            }
            password.length < 8 -> {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = authRepository.signUp(
                email = trimmedEmail,
                password = password,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = trimmedPhone
            )

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSignUpSuccessful = true,
                            registeredEmail = trimmedEmail,
                            registeredPhone = trimmedPhone
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

    fun consumeSignUpSuccess() {
        _uiState.update { it.copy(isSignUpSuccessful = false) }
    }
}
