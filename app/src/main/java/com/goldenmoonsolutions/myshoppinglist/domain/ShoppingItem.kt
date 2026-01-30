package com.goldenmoonsolutions.myshoppinglist.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ShoppingItem(
    val id: Long? = null,
    val name: String,
    val quantity: String = "1",
    val category: String = ShoppingCategory.GENERAL.name,
    @SerialName("is_purchased")
    val isPurchased: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("user_email")
    val userEmail: String? = null
)