package com.tanaw.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val phone: String = "",
    val city: String = "Cabanatuan City",
    val address: String = "Cabanatuan City, NE",
    @SerialName("created_at") val createdAt: String = ""
)
