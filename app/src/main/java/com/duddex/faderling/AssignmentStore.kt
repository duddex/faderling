package com.duddex.faderling

import android.content.Context

class AssignmentStore(context: Context) {
    private val prefs = context.getSharedPreferences("assignments", Context.MODE_PRIVATE)

    fun loadAll(): List<Assignment> = (0 until Assignment.FADER_COUNT).map(::load)

    fun load(index: Int): Assignment {
        val default = Assignment.default(index)
        return Assignment(
            name = prefs.getString("name_$index", null) ?: default.name,
            cc = prefs.getInt("cc_$index", default.cc),
            channel = prefs.getInt("channel_$index", default.channel),
        )
    }

    fun save(index: Int, assignment: Assignment) {
        prefs.edit()
            .putString("name_$index", assignment.name)
            .putInt("cc_$index", assignment.cc)
            .putInt("channel_$index", assignment.channel)
            .apply()
    }
}
