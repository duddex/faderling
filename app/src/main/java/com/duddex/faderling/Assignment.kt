package com.duddex.faderling

data class Assignment(val name: String, val cc: Int, val channel: Int) {
    companion object {
        const val FADER_COUNT = 10
        const val MAX_NAME_LENGTH = 12
        val CC_RANGE = 0..127
        val CHANNEL_RANGE = 1..16

        fun default(index: Int): Assignment = Assignment(name = "CC ${20 + index}", cc = 20 + index, channel = 1)
    }
}
