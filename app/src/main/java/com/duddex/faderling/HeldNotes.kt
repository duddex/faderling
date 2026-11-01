package com.duddex.faderling

/**
 * Notes held on the Keyboard, ordered by when they were first pressed. With Hold on, a Note stays after
 * its Key is released; the first Key pressed after all Keys were released starts a new latched chord.
 */
class HeldNotes {
    private val physical = LinkedHashMap<Int, Int>()
    private val latched = LinkedHashSet<Int>()

    var hold = false
        private set

    /** Registers a pressed Key and returns the Notes that are now held, in order. */
    fun press(key: Int, note: Int): List<Int> {
        if (hold && physical.isEmpty()) latched.clear()
        physical[key] = note
        if (hold) latched += note
        return notes()
    }

    /** Registers a released Key and returns the Notes that are now held, in order. */
    fun release(key: Int): List<Int> {
        physical.remove(key)
        return notes()
    }

    /** Turns Hold on or off; when it is turned on, Notes that are currently down become latched. */
    fun setHold(on: Boolean): List<Int> {
        if (hold != on) {
            hold = on
            if (on) latched.addAll(physical.values) else latched.clear()
        }
        return notes()
    }

    /** Notes to sound, in the order they were first pressed. */
    fun notes(): List<Int> = if (hold) latched.toList() else physical.values.toList()

    /** Notes kept after their Key was released, for the Keyboard highlight. */
    fun latchedNotes(): Set<Int> = if (hold) latched.toSet() else emptySet()

    fun clear() {
        physical.clear()
        latched.clear()
        hold = false
    }
}
