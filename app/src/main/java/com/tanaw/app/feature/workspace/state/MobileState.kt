package com.tanaw.app.feature.workspace.state

import android.graphics.Bitmap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class RestoredBookingConfirmation(
    val id: String,
    val trackingNumber: String,
    val pickup: String,
    val dropoff: String,
    val vehicleId: String,
    val scheduledDeparture: String
)

data class MobileState(
    val page: String = "loading",
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val data: JsonObject? = null,
    val email: String = "",
    val resendSeconds: Int = 0,
    val details: JsonObject? = null,
    val detailLoading: Boolean = false,
    val proof: Bitmap? = null,
    val proofTitle: String = "",
    val proofSubtitle: String = "",
    val offline: Boolean = false,
    val lastSync: String = "",
    val history: JsonObject? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val conversationUnread: JsonArray? = null,
    val passwordChangeOwner: String? = null,
    val passwordChangeCodeSent: Boolean = false,
    val restoredBookingConfirmation: RestoredBookingConfirmation? = null,
    val recoveryCode: String? = null,
    val customerShipments: List<JsonObject>? = null,
    val customerShipmentsLoading: Boolean = false,
    val customerShipmentsHasMore: Boolean = false,
    val customerShipmentsError: String? = null,
    val customerShipmentsTab: String? = null,
    val customerShipmentsFilter: String? = null,
    val customerHistoryDocuments: List<JsonObject> = emptyList(),
    val notificationsPage: List<JsonObject>? = null,
    val notificationsLoading: Boolean = false,
    val notificationsHasMore: Boolean = false,
    val notificationsError: String? = null,
    val phoneChangeTarget: String? = null,
    val phoneChangePendingName: String? = null,
    val phoneChangePendingAddress: String? = null,
    val phoneChangePendingCity: String? = null
)
