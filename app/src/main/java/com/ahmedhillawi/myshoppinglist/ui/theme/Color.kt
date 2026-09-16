package com.ahmedhillawi.myshoppinglist.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette derived (same hue family, standard M3 tonal steps) from the app icon's blue
// (#93C2F5, see ic_launcher_background.xml) so the in-app color scheme matches the launcher icon
// instead of the unbranded Compose-template purple this replaced. Used as the fallback
// ColorScheme on devices where Material You dynamic color isn't available (pre-Android 12, or
// disabled by the user) -- see Theme.kt.

// Primary -- the brand hue itself.
val BrandPrimaryLight = Color(0xFF1B68BB)
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandPrimaryContainerLight = Color(0xFFC6DFFA)
val BrandOnPrimaryContainerLight = Color(0xFF122D49)

val BrandPrimaryDark = Color(0xFFA0C5EE)
val BrandOnPrimaryDark = Color(0xFF122D49)
val BrandPrimaryContainerDark = Color(0xFF224B77)
val BrandOnPrimaryContainerDark = Color(0xFFD1E5FA)

// Secondary -- same hue, desaturated toward neutral; supporting UI (e.g. the top bar's household
// name line).
val BrandSecondaryLight = Color(0xFF4D6580)
val BrandOnSecondaryLight = Color(0xFFFFFFFF)
val BrandSecondaryContainerLight = Color(0xFFDEE5ED)
val BrandOnSecondaryContainerLight = Color(0xFF263240)

val BrandSecondaryDark = Color(0xFFC2CCD6)
val BrandOnSecondaryDark = Color(0xFF263240)
val BrandSecondaryContainerDark = Color(0xFF3D4C5C)
val BrandOnSecondaryContainerDark = Color(0xFFDFE5EC)

// Tertiary -- complementary violet accent (+60° hue), reserved for rare highlights.
val BrandTertiaryLight = Color(0xFF683894)
val BrandOnTertiaryLight = Color(0xFFFFFFFF)
val BrandTertiaryContainerLight = Color(0xFFE1D0F1)
val BrandOnTertiaryContainerLight = Color(0xFF2F1C40)

val BrandTertiaryDark = Color(0xFFCDB5E3)
val BrandOnTertiaryDark = Color(0xFF2F1C40)
val BrandTertiaryContainerDark = Color(0xFF4E3267)
val BrandOnTertiaryContainerDark = Color(0xFFE6D7F4)
