package com.example.sparely.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Sparely brand =====
// The logo's colors. Splash, lock screen, widget and the dashboard hero use them as-is.
val BrandSpruce = Color(0xFF0F3D34)
val BrandDeepSpruce = Color(0xFF0A2C26)
val BrandMint = Color(0xFF8FE3C0)
val BrandGold = Color(0xFFF4CB6A)
val BrandInk = Color(0xFFEAF5F0)
val BrandMuted = Color(0xFF9DBDB2)

// ===== App color scheme, built around the brand =====
// Used by default. On Android 12+ the user can switch to wallpaper colors in Settings > Appearance.

// Light
val SparelyPrimary = Color(0xFF1B6B58)            // spruce, light enough for buttons on white
val SparelyOnPrimary = Color(0xFFFFFFFF)
val SparelyPrimaryContainer = Color(0xFFB4F1D7)   // mint
val SparelyOnPrimaryContainer = Color(0xFF002118)
val SparelySecondary = Color(0xFF7A5A00)          // gold, readable on white
val SparelyOnSecondary = Color(0xFFFFFFFF)
val SparelySecondaryContainer = Color(0xFFFFDF9E)
val SparelyOnSecondaryContainer = Color(0xFF261A00)
val SparelyTertiary = Color(0xFF4B6358)           // quiet green-grey
val SparelyOnTertiary = Color(0xFFFFFFFF)
val SparelyTertiaryContainer = Color(0xFFCDE9DA)
val SparelyOnTertiaryContainer = Color(0xFF082016)
val SparelyBackground = Color(0xFFF5FBF7)
val SparelyOnBackground = Color(0xFF171D1A)
val SparelySurfaceVariant = Color(0xFFDBE5DF)
val SparelyOnSurfaceVariant = Color(0xFF404944)
val SparelyOutline = Color(0xFF707973)
val SparelyOutlineVariant = Color(0xFFBFC9C3)
val SparelySurfaceContainerLowest = Color(0xFFFFFFFF)
val SparelySurfaceContainerLow = Color(0xFFEFF5F1)
val SparelySurfaceContainer = Color(0xFFE9EFEB)
val SparelySurfaceContainerHigh = Color(0xFFE3EAE5)
val SparelySurfaceContainerHighest = Color(0xFFDEE4E0)
val SparelySurfaceDim = Color(0xFFD5DBD8)
val SparelyInverseSurface = Color(0xFF2C322F)
val SparelyInverseOnSurface = Color(0xFFECF2EE)

// Dark
val SparelyPrimaryDark = BrandMint
val SparelyOnPrimaryDark = Color(0xFF00382A)
val SparelyPrimaryContainerDark = BrandSpruce
val SparelyOnPrimaryContainerDark = Color(0xFFB4F1D7)
val SparelySecondaryDark = BrandGold
val SparelyOnSecondaryDark = Color(0xFF402D00)
val SparelySecondaryContainerDark = Color(0xFF5C4300)
val SparelyOnSecondaryContainerDark = Color(0xFFFFDF9E)
val SparelyTertiaryDark = Color(0xFFB1CCBE)
val SparelyOnTertiaryDark = Color(0xFF1D352A)
val SparelyTertiaryContainerDark = Color(0xFF334B40)
val SparelyOnTertiaryContainerDark = Color(0xFFCDE9DA)
val SparelyBackgroundDark = Color(0xFF0E1512)
val SparelyOnBackgroundDark = Color(0xFFDEE4E0)
val SparelySurfaceVariantDark = Color(0xFF404944)
val SparelyOnSurfaceVariantDark = Color(0xFFBFC9C3)
val SparelyOutlineDark = Color(0xFF89938D)
val SparelyOutlineVariantDark = Color(0xFF404944)
val SparelySurfaceContainerLowestDark = Color(0xFF09100D)
val SparelySurfaceContainerLowDark = Color(0xFF171D1A)
val SparelySurfaceContainerDark = Color(0xFF1B211E)
val SparelySurfaceContainerHighDark = Color(0xFF252B28)
val SparelySurfaceContainerHighestDark = Color(0xFF303633)
val SparelySurfaceBrightDark = Color(0xFF343B37)
val SparelyInverseSurfaceDark = Color(0xFFDEE4E0)
val SparelyInverseOnSurfaceDark = Color(0xFF2C322F)

// ===== Money colors =====
// Fixed in every theme, wallpaper colors included, so "saved", "goal" and "alert" always mean the
// same thing. Light values are darker so they stay readable on light surfaces.
val SavedLight = Color(0xFF1B7A58)
val OnSavedLight = Color(0xFFFFFFFF)
val SavedContainerLight = Color(0xFFC9F2DE)
val OnSavedContainerLight = Color(0xFF00281C)
val SavedDark = BrandMint
val OnSavedDark = Color(0xFF00382A)
val SavedContainerDark = BrandSpruce
val OnSavedContainerDark = Color(0xFFB4F1D7)

val GoalLight = Color(0xFF8A6400)
val OnGoalLight = Color(0xFFFFFFFF)
val GoalContainerLight = Color(0xFFFFE9B8)
val OnGoalContainerLight = Color(0xFF2B1D00)
val GoalDark = BrandGold
val OnGoalDark = Color(0xFF402D00)
val GoalContainerDark = Color(0xFF4D3900)
val OnGoalContainerDark = Color(0xFFFFDF9E)

val AlertLight = Color(0xFFBA1A1A)
val OnAlertLight = Color(0xFFFFFFFF)
val AlertContainerLight = Color(0xFFFFDAD6)
val OnAlertContainerLight = Color(0xFF410002)
val AlertDark = Color(0xFFFFB4AB)
val OnAlertDark = Color(0xFF690005)
val AlertContainerDark = Color(0xFF93000A)
val OnAlertContainerDark = Color(0xFFFFDAD6)
