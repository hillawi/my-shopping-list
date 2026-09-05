package com.ahmedhillawi.myshoppinglist.domain

import androidx.annotation.StringRes
import com.ahmedhillawi.myshoppinglist.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementUnit(@StringRes val resId: Int) {
    PACK(R.string.unit_pack),
    ML(R.string.unit_ml),
    @SerialName("L")
    LITRE(R.string.unit_l),
    @SerialName("G")
    GRAM(R.string.unit_g),
    KG(R.string.unit_kg),
    PCS(R.string.unit_pcs)
}