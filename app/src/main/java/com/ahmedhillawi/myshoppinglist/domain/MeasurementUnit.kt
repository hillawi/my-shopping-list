package com.ahmedhillawi.myshoppinglist.domain

import androidx.annotation.StringRes
import com.ahmedhillawi.myshoppinglist.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementUnit(@StringRes val resId: Int, val step: Double) {
    PACK(R.string.unit_pack, 1.0),
    ML(R.string.unit_ml, 50.0),
    @SerialName("L")
    LITRE(R.string.unit_l, 0.5),
    @SerialName("G")
    GRAM(R.string.unit_g, 50.0),
    KG(R.string.unit_kg, 0.5),
    PCS(R.string.unit_pcs, 1.0)
}