package com.goldenmoonsolutions.myshoppinglist.domain

import androidx.annotation.StringRes
import com.goldenmoonsolutions.myshoppinglist.R
import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementUnit(@StringRes val resId: Int) {
    PACK(R.string.unit_pack),
    ML(R.string.unit_ml),
    LITRE(R.string.unit_l),
    GRAM(R.string.unit_g),
    KG(R.string.unit_kg),
    PCS(R.string.unit_pcs)
}