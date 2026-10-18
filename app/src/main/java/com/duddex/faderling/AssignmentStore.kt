package com.duddex.faderling

import android.content.Context

class AssignmentStore(context: Context) {
    private val prefs = context.getSharedPreferences("assignments", Context.MODE_PRIVATE)

    fun loadAll(kind: ControlKind): List<Assignment> = (0 until kind.count).map { load(kind, it) }

    fun load(kind: ControlKind, index: Int): Assignment {
        val default = Assignment.default(kind, index)
        val p = kind.keyPrefix
        return Assignment(
            name = prefs.getString("${p}name_$index", null) ?: default.name,
            cc = prefs.getInt("${p}cc_$index", default.cc),
            channel = prefs.getInt("${p}channel_$index", default.channel),
            mode = prefs.getString("${p}mode_$index", null)
                ?.let { runCatching { ButtonMode.valueOf(it) }.getOrNull() } ?: default.mode,
        )
    }

    fun save(kind: ControlKind, index: Int, assignment: Assignment) {
        val p = kind.keyPrefix
        val editor = prefs.edit()
            .putString("${p}name_$index", assignment.name)
            .putInt("${p}cc_$index", assignment.cc)
            .putInt("${p}channel_$index", assignment.channel)
        if (kind == ControlKind.BUTTON) editor.putString("${p}mode_$index", assignment.mode.name)
        editor.apply()
    }

    fun loadKeyboard(): KeyboardSettings {
        val default = KeyboardSettings()
        return KeyboardSettings(
            channel = prefs.getInt("keyboard_channel", default.channel),
            velocity = prefs.getInt("keyboard_velocity", default.velocity),
        )
    }

    fun saveKeyboard(settings: KeyboardSettings) {
        prefs.edit()
            .putInt("keyboard_channel", settings.channel)
            .putInt("keyboard_velocity", settings.velocity)
            .apply()
    }

    fun loadPads(): List<PadAssignment> = (0 until Pads.COUNT).map { i ->
        val default = Pads.default(i)
        PadAssignment(
            name = prefs.getString("pad_name_$i", null) ?: default.name,
            note = prefs.getInt("pad_note_$i", default.note),
        )
    }

    fun savePad(index: Int, pad: PadAssignment) {
        prefs.edit()
            .putString("pad_name_$index", pad.name)
            .putInt("pad_note_$index", pad.note)
            .apply()
    }

    fun loadPadSettings(): PadSettings {
        val default = PadSettings()
        return PadSettings(
            channel = prefs.getInt("pads_channel", default.channel),
            velocity = prefs.getInt("pads_velocity", default.velocity),
        )
    }

    fun savePadSettings(settings: PadSettings) {
        prefs.edit()
            .putInt("pads_channel", settings.channel)
            .putInt("pads_velocity", settings.velocity)
            .apply()
    }
}
