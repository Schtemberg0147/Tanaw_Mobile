package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable

@Immutable
data class SubmitPodUiState(
    val deliveryId: String = "",
    val trackingNumber: String = "",
    val recipientName: String = "",
    val hasCargoPhoto: Boolean = false,
    val cargoPhotoBytes: ByteArray? = null,
    val hasSignature: Boolean = false,
    val signatureBytes: ByteArray? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
) {
    val isSubmitEnabled: Boolean
        get() = recipientName.isNotBlank() && hasCargoPhoto && hasSignature && !isSubmitting

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SubmitPodUiState
        if (deliveryId != other.deliveryId) return false
        if (trackingNumber != other.trackingNumber) return false
        if (recipientName != other.recipientName) return false
        if (hasCargoPhoto != other.hasCargoPhoto) return false
        if (cargoPhotoBytes != null) {
            if (other.cargoPhotoBytes == null) return false
            if (!cargoPhotoBytes.contentEquals(other.cargoPhotoBytes)) return false
        } else if (other.cargoPhotoBytes != null) return false
        if (hasSignature != other.hasSignature) return false
        if (signatureBytes != null) {
            if (other.signatureBytes == null) return false
            if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        } else if (other.signatureBytes != null) return false
        if (isSubmitting != other.isSubmitting) return false
        if (errorMessage != other.errorMessage) return false
        if (isSuccess != other.isSuccess) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deliveryId.hashCode()
        result = 31 * result + trackingNumber.hashCode()
        result = 31 * result + recipientName.hashCode()
        result = 31 * result + hasCargoPhoto.hashCode()
        result = 31 * result + (cargoPhotoBytes?.contentHashCode() ?: 0)
        result = 31 * result + hasSignature.hashCode()
        result = 31 * result + (signatureBytes?.contentHashCode() ?: 0)
        result = 31 * result + isSubmitting.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + isSuccess.hashCode()
        return result
    }
}

sealed interface SubmitPodSideEffect {
    data class NavigateToDeliveryComplete(val deliveryId: String) : SubmitPodSideEffect
    data object NavigateBack : SubmitPodSideEffect
}

sealed interface SubmitPodUiEvent {
    data class RecipientNameChanged(val name: String) : SubmitPodUiEvent
    data class CargoPhotoSelected(val bytes: ByteArray?) : SubmitPodUiEvent
    data class SignatureCaptured(val bytes: ByteArray?) : SubmitPodUiEvent
    data object SubmitPod : SubmitPodUiEvent
    data object DismissError : SubmitPodUiEvent
}
