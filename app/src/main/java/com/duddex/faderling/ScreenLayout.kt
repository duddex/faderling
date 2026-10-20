package com.duddex.faderling

/** Decides between the regular layout and the compact layout for low screens such as phones in landscape. */
object ScreenLayout {
    /** Below this available height (dp) the compact layout is used; both test tablets are well above it. */
    const val COMPACT_MAX_HEIGHT_DP = 450

    fun isCompact(availableHeightDp: Int): Boolean = availableHeightDp < COMPACT_MAX_HEIGHT_DP
}
