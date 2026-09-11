package com.ahmedhillawi.myshoppinglist.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HouseholdMember(
    @SerialName("household_id")
    val householdId: String,
    @SerialName("user_id")
    val userId: String,
    val role: String = "member",
    @SerialName("joined_at")
    val joinedAt: String? = null
)
