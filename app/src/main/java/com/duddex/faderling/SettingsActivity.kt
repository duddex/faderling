package com.duddex.faderling

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {

    private class Row(
        val kind: ControlKind,
        val index: Int,
        val name: EditText,
        val cc: EditText,
        val channel: EditText,
        val mode: TextView?,
        val warning: TextView,
    ) {
        var current: Assignment? = null
    }

    private lateinit var store: AssignmentStore
    private val rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AssignmentStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        root.addView(header())

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section(list, ControlKind.FADER, R.string.section_faders, R.string.fader_number)
        section(list, ControlKind.KNOB, R.string.section_knobs, R.string.knob_number)
        section(list, ControlKind.BUTTON, R.string.section_buttons, R.string.button_number)
        keyboardSection(list)
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        root.padForSystemBars()
        updateWarnings()
    }

    private fun header(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageButton(this@SettingsActivity).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(resolveAttr(android.R.attr.selectableItemBackgroundBorderless))
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(text(getString(R.string.settings), Palette.TEXT, 20f).apply { setPadding(dp(8), 0, 0, 0) })
    }

    private fun section(list: LinearLayout, kind: ControlKind, titleRes: Int, labelRes: Int) {
        list.addView(text(getString(titleRes), Palette.ACCENT, 16f).apply { setPadding(0, dp(16), 0, dp(2)) })
        list.addView(columnLabels(kind == ControlKind.BUTTON))
        store.loadAll(kind).forEachIndexed { i, a -> list.addView(row(kind, i, a, getString(labelRes, i + 1))) }
    }

    private fun keyboardSection(list: LinearLayout) {
        val settings = store.loadKeyboard()
        list.addView(text(getString(R.string.section_keyboard), Palette.ACCENT, 16f).apply { setPadding(0, dp(16), 0, dp(2)) })
        list.addView(LinearLayout(this).apply {
            addView(View(this@SettingsActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(text(getString(R.string.label_velocity), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(getString(R.string.label_channel), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(MODE_WIDTH), 0))
        })

        val velocity = edit(settings.velocity.toString(), InputType.TYPE_CLASS_NUMBER, 3)
        val channel = edit(settings.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = velocity.text.toString().toIntOrNull()?.takeIf { it in KeyboardSettings.VELOCITY_RANGE }
                val c = channel.text.toString().toIntOrNull()?.takeIf { it in Assignment.CHANNEL_RANGE }
                velocity.error = if (v == null) getString(R.string.error_velocity) else null
                channel.error = if (c == null) getString(R.string.error_channel) else null
                if (v != null && c != null) store.saveKeyboard(KeyboardSettings(channel = c, velocity = v))
            }
        }
        velocity.addTextChangedListener(watcher)
        channel.addTextChangedListener(watcher)

        list.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(getString(R.string.page_keyboard), Palette.TEXT, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(velocity, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(channel, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(MODE_WIDTH), 0))
        })
    }

    private fun columnLabels(withMode: Boolean): View = LinearLayout(this).apply {
        addView(text("", Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_name), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(getString(R.string.label_cc), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_channel), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(if (withMode) getString(R.string.label_mode) else "", Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(MODE_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun row(kind: ControlKind, index: Int, a: Assignment, label: String): View {
        val name = edit(a.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
        val cc = edit(a.cc.toString(), InputType.TYPE_CLASS_NUMBER, 3)
        val channel = edit(a.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val mode = if (kind == ControlKind.BUTTON) modeSwitch(a.mode) else null
        val warning = text("", Palette.ACCENT, 13f).apply { visibility = View.GONE }
        val r = Row(kind, index, name, cc, channel, mode, warning).apply { current = a }
        rows += r

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onRowEdited(r)
        }
        name.addTextChangedListener(watcher)
        cc.addTextChangedListener(watcher)
        channel.addTextChangedListener(watcher)
        mode?.setOnClickListener {
            it.tag = if (it.tag == ButtonMode.LATCH) ButtonMode.MOMENTARY else ButtonMode.LATCH
            showMode(it as TextView)
            onRowEdited(r)
        }

        val line = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(label, Palette.TEXT, 16f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(cc, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(channel, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(mode ?: View(this@SettingsActivity), LinearLayout.LayoutParams(dp(MODE_WIDTH), dp(36)))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(line)
            addView(warning, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(80) })
        }
    }

    private fun modeSwitch(initial: ButtonMode) = TextView(this).apply {
        tag = initial
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), Palette.TEXT_DIM)
        }
        showMode(this)
    }

    private fun showMode(view: TextView) {
        val latch = view.tag == ButtonMode.LATCH
        view.text = getString(if (latch) R.string.mode_latch else R.string.mode_momentary)
        view.setTextColor(if (latch) Palette.ACCENT else Palette.TEXT)
    }

    private fun onRowEdited(r: Row) {
        val cc = r.cc.text.toString().toIntOrNull()?.takeIf { it in Assignment.CC_RANGE }
        val channel = r.channel.text.toString().toIntOrNull()?.takeIf { it in Assignment.CHANNEL_RANGE }
        r.cc.error = if (cc == null) getString(R.string.error_cc) else null
        r.channel.error = if (channel == null) getString(R.string.error_channel) else null
        if (cc != null && channel != null) {
            val mode = r.mode?.tag as? ButtonMode ?: ButtonMode.MOMENTARY
            val a = Assignment(r.name.text.toString(), cc, channel, mode)
            r.current = a
            store.save(r.kind, r.index, a)
            updateWarnings()
        }
    }

    private fun updateWarnings() {
        rows.forEach { r ->
            val a = r.current ?: return@forEach
            val others = rows.filter { o -> o !== r && o.current?.cc == a.cc && o.current?.channel == a.channel }
            if (others.isEmpty()) {
                r.warning.visibility = View.GONE
            } else {
                r.warning.text = getString(R.string.warning_duplicate, others.joinToString(", ") { label(it) })
                r.warning.visibility = View.VISIBLE
            }
        }
    }

    private fun label(r: Row): String = getString(
        when (r.kind) {
            ControlKind.FADER -> R.string.fader_number
            ControlKind.KNOB -> R.string.knob_number
            ControlKind.BUTTON -> R.string.button_number
        },
        r.index + 1,
    )

    private fun edit(value: String, type: Int, maxLength: Int) = EditText(this).apply {
        setText(value)
        inputType = type
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NEXT
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        setSingleLine()
        setTextColor(Palette.TEXT)
    }

    private fun text(value: String, color: Int, sizeSp: Float) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val MODE_WIDTH = 110
    }
}
