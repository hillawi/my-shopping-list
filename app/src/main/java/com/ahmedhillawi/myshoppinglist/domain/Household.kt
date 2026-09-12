package com.ahmedhillawi.myshoppinglist.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val id: String? = null,
    val name: String,
    @SerialName("invite_code")
    val inviteCode: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
