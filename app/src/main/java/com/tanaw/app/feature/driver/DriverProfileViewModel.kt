package com.tanaw.app.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverProfileViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<DriverProfileUiState>(DriverProfileUiState.Loading)
    val uiState: StateFlow<DriverProfileUiState> = _uiState.asStateFlow()

    private val _sideEffectChannel = Channel<DriverProfileSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    init {
        loadProfileData()
    }

    fun loadProfileData(isRefreshing: Boolean = false) {
        viewModelScope.launch(ioDispatcher) {
            if (!isRefreshing) {
                _uiState.value = DriverProfileUiState.Loading
            }

            val detailsDeferred = async { driverRepository.observeDriverProfileDetails().first() }
            val docsDeferred = async { driverRepository.observeComplianceDocuments().first() }

            val detailsResult = detailsDeferred.await()
            val docsResult = docsDeferred.await()

            if (detailsResult.isFailure) {
                val err = detailsResult.exceptionOrNull()?.message ?: "Failed to load driver profile details."
                _uiState.value = DriverProfileUiState.Error(err)
            } else {
                val details = detailsResult.getOrThrow()
                val (docsList, docsErr) = if (docsResult.isSuccess) {
                    Pair(docsResult.getOrThrow(), null)
                } else {
                    Pair(emptyList<ComplianceDocumentItem>(), docsResult.exceptionOrNull()?.message ?: "Failed to load compliance documents.")
                }

                _uiState.value = DriverProfileUiState.Content(
                    details = details,
                    complianceDocs = docsList,
                    isRefreshing = false,
                    docsErrorMessage = docsErr
                )
            }
        }
    }

    fun onEvent(event: DriverProfileEvent) {
        when (event) {
            DriverProfileEvent.Refresh -> loadProfileData(isRefreshing = true)
            DriverProfileEvent.Retry -> loadProfileData(isRefreshing = false)
            DriverProfileEvent.RetryDocs -> {
                viewModelScope.launch(ioDispatcher) {
                    val currentContent = _uiState.value as? DriverProfileUiState.Content ?: return@launch
                    val docsResult = driverRepository.observeComplianceDocuments().first()

                    if (docsResult.isSuccess) {
                        _uiState.value = currentContent.copy(
                            complianceDocs = docsResult.getOrThrow(),
                            docsErrorMessage = null
                        )
                    } else {
                        _uiState.value = currentContent.copy(
                            docsErrorMessage = docsResult.exceptionOrNull()?.message ?: "Failed to reload compliance documents."
                        )
                    }
                }
            }
            DriverProfileEvent.LogoutClicked -> {
                viewModelScope.launch(ioDispatcher) {
                    driverRepository.signOut()
                    _sideEffectChannel.send(DriverProfileSideEffect.NavigateToLogin)
                }
            }
        }
    }
}
