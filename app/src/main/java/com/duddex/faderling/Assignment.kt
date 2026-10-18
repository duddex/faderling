package com.duddex.faderling

enum class ButtonMode { MOMENTARY, LATCH }

enum class ControlKind(val count: Int, val defaultFirstCc: Int, val keyPrefix: String) {
    FADER(10, 20, ""),
    KNOB(8, 102, "knob_"),
    BUTTON(8, 110, "button_"),
}

data class Assignment(val name: String, val cc: Int, val channel: Int, val mode: ButtonMode = ButtonMode.MOMENTARY) {
    companion object {
        const val MAX_NAME_LENGTH = 12
        val CC_RANGE = 0..127
        val CHANNEL_RANGE = 1..16

        fun default(kind: ControlKind, index: Int): Assignment {
            val cc = kind.defaultFirstCc + index
            return Assignment(name = "CC $cc", cc = cc, channel = 1)
        }
    }
}
