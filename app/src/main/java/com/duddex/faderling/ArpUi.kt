package com.duddex.faderling

import android.content.Context

/** The Arp Pattern name as shown on the Keyboard Page and in the settings. */
fun patternLabel(context: Context, pattern: ArpPattern): String = context.getString(
    when (pattern) {
        ArpPattern.UP -> R.string.arp_pattern_up
        ArpPattern.DOWN -> R.string.arp_pattern_down
        ArpPattern.UP_DOWN -> R.string.arp_pattern_up_down
        ArpPattern.RANDOM -> R.string.arp_pattern_random
        ArpPattern.AS_PLAYED -> R.string.arp_pattern_as_played
    },
)
