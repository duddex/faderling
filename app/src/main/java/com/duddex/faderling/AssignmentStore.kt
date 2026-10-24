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

    fun loadLfoAssignments(): List<LfoAssignment> = (0 until Lfos.COUNT).map { i ->
        val default = Lfos.default(i)
        LfoAssignment(
            name = prefs.getString("lfo_name_$i", null) ?: default.name,
            cc = prefs.getInt("lfo_cc_$i", default.cc),
            channel = prefs.getInt("lfo_channel_$i", default.channel),
        )
    }

    fun saveLfoAssignment(index: Int, lfo: LfoAssignment) {
        prefs.edit()
            .putString("lfo_name_$index", lfo.name)
            .putInt("lfo_cc_$index", lfo.cc)
            .putInt("lfo_channel_$index", lfo.channel)
            .apply()
    }

    fun loadLfoRanges(): List<LfoRange> = (0 until Lfos.COUNT).map { i ->
        val default = LfoRange()
        LfoRange(
            min = prefs.getInt("lfo_min_$i", default.min),
            max = prefs.getInt("lfo_max_$i", default.max),
            speedSeconds = prefs.getFloat("lfo_speed_$i", default.speedSeconds),
        )
    }

    fun saveLfoRange(index: Int, range: LfoRange) {
        prefs.edit()
            .putInt("lfo_min_$index", range.min)
            .putInt("lfo_max_$index", range.max)
            .putFloat("lfo_speed_$index", range.speedSeconds)
            .apply()
    }

    fun loadXyAssignments(): List<XyAssignment> = (0 until XyPads.COUNT).map { i ->
        val default = XyPads.default(i)
        XyAssignment(
            name = prefs.getString("xy_name_$i", null) ?: default.name,
            ccX = prefs.getInt("xy_ccx_$i", default.ccX),
            ccY = prefs.getInt("xy_ccy_$i", default.ccY),
            channel = prefs.getInt("xy_channel_$i", default.channel),
        )
    }

    fun saveXyAssignment(index: Int, xy: XyAssignment) {
        prefs.edit()
            .putString("xy_name_$index", xy.name)
            .putInt("xy_ccx_$index", xy.ccX)
            .putInt("xy_ccy_$index", xy.ccY)
            .putInt("xy_channel_$index", xy.channel)
            .apply()
    }
}
