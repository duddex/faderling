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
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabs: List<TextView>
    private var page = 0
    private val rows = mutableListOf<Row>()

    /** Every Control's Assignment, including those of other Pages, for duplicate warnings. */
    private val assignments = HashMap<Pair<ControlKind, Int>, Assignment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AssignmentStore(this)
        ControlKind.entries.forEach { kind -> store.loadAll(kind).forEachIndexed { i, a -> assignments[kind to i] = a } }

        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        list = findViewById(R.id.list)
        scroll = findViewById(R.id.scroll)
        tabs = listOf(R.id.tabFaders, R.id.tabButtons, R.id.tabKeyboard, R.id.tabPads, R.id.tabLfos, R.id.tabXy).mapIndexed { p, id ->
            findViewById<TextView>(id).apply { setOnClickListener { showPage(p) } }
        }
        showPage(savedInstanceState?.getInt(STATE_PAGE) ?: intent.getIntExtra(EXTRA_PAGE, 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    /** Shows the settings belonging to one Page of the main screen. */
    private fun showPage(p: Int) {
        page = p
        tabs.forEachIndexed { i, t -> t.isSelected = i == p }
        currentFocus?.clearFocus()
        list.removeAllViews()
        rows.clear()
        when (p) {
            0 -> section(list, ControlKind.FADER, R.string.section_faders, R.string.fader_number)
            1 -> section(list, ControlKind.BUTTON, R.string.section_buttons, R.string.button_number)
            2 -> keyboardSection(list)
            3 -> padsSection(list)
            4 -> lfoSection(list)
            5 -> xySection(list)
        }
        scroll.scrollTo(0, 0)
        updateWarnings()
    }

    private fun section(list: LinearLayout, kind: ControlKind, titleRes: Int, labelRes: Int) {
        list.addView(text(getString(titleRes), Palette.ACCENT, 16f).apply { setPadding(0, dp(12), 0, dp(2)) })
        if (kind == ControlKind.FADER) list.addView(text(getString(R.string.faders_hint), Palette.TEXT_DIM, 13f))
        list.addView(columnLabels(kind))
        val maxes = if (kind == ControlKind.FADER) store.loadFaderMaxes() else emptyList()
        (0 until kind.count).forEach { i ->
            list.addView(row(kind, i, assignments.getValue(kind to i), getString(labelRes, i + 1), maxes.getOrNull(i)))
        }
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

    private fun padsSection(list: LinearLayout) {
        list.addView(text(getString(R.string.section_pads), Palette.ACCENT, 16f).apply { setPadding(0, dp(16), 0, dp(2)) })
        list.addView(text(getString(R.string.pads_hint), Palette.TEXT_DIM, 13f))
        list.addView(LinearLayout(this).apply {
            setPadding(0, dp(4), 0, 0)
            addView(text("", Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(getString(R.string.label_name), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(getString(R.string.label_note), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(80 + MODE_WIDTH), 0))
        })

        store.loadPads().forEachIndexed { i, pad ->
            val name = edit(pad.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
            val note = edit(pad.note.toString(), InputType.TYPE_CLASS_NUMBER, 3)
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val n = note.text.toString().toIntOrNull()?.takeIf { it in 0..127 }
                    note.error = if (n == null) getString(R.string.error_note) else null
                    if (n != null) store.savePad(i, PadAssignment(name.text.toString(), n))
                }
            }
            name.addTextChangedListener(watcher)
            note.addTextChangedListener(watcher)
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(getString(R.string.pad_number, i + 1), Palette.TEXT, 16f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(note, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(80 + MODE_WIDTH), 0))
            })
        }

        val settings = store.loadPadSettings()
        list.addView(LinearLayout(this).apply {
            setPadding(0, dp(8), 0, 0)
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
                if (v != null && c != null) store.savePadSettings(PadSettings(channel = c, velocity = v))
            }
        }
        velocity.addTextChangedListener(watcher)
        channel.addTextChangedListener(watcher)
        list.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(getString(R.string.pads_all), Palette.TEXT, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(velocity, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(channel, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(MODE_WIDTH), 0))
        })
    }

    private fun lfoSection(list: LinearLayout) {
        list.addView(text(getString(R.string.section_lfos), Palette.ACCENT, 16f).apply { setPadding(0, dp(12), 0, dp(2)) })
        list.addView(text(getString(R.string.lfos_hint), Palette.TEXT_DIM, 13f))
        list.addView(columnLabels(null))
        store.loadLfoAssignments().forEachIndexed { i, lfo ->
            val name = edit(lfo.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
            val cc = edit(lfo.cc.toString(), InputType.TYPE_CLASS_NUMBER, 3)
            val channel = edit(lfo.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val c = cc.text.toString().toIntOrNull()?.takeIf { it in Assignment.CC_RANGE }
                    val ch = channel.text.toString().toIntOrNull()?.takeIf { it in Assignment.CHANNEL_RANGE }
                    cc.error = if (c == null) getString(R.string.error_cc) else null
                    channel.error = if (ch == null) getString(R.string.error_channel) else null
                    if (c != null && ch != null) store.saveLfoAssignment(i, LfoAssignment(name.text.toString(), c, ch))
                }
            }
            name.addTextChangedListener(watcher)
            cc.addTextChangedListener(watcher)
            channel.addTextChangedListener(watcher)
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(getString(R.string.lfo_number, i + 1), Palette.TEXT, 16f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(cc, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(channel, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(View(this@SettingsActivity), LinearLayout.LayoutParams(dp(MODE_WIDTH), 0))
            })
        }
    }

    private fun xySection(list: LinearLayout) {
        list.addView(text(getString(R.string.section_xy), Palette.ACCENT, 16f).apply { setPadding(0, dp(12), 0, dp(2)) })
        list.addView(text(getString(R.string.xy_hint), Palette.TEXT_DIM, 13f))
        list.addView(LinearLayout(this).apply {
            setPadding(0, dp(4), 0, 0)
            addView(text("", Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(getString(R.string.label_name), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(getString(R.string.label_cc_x), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(getString(R.string.label_cc_y), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(getString(R.string.label_channel), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(MODE_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        store.loadXyAssignments().forEachIndexed { i, xy ->
            val name = edit(xy.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
            val ccX = edit(xy.ccX.toString(), InputType.TYPE_CLASS_NUMBER, 3)
            val ccY = edit(xy.ccY.toString(), InputType.TYPE_CLASS_NUMBER, 3)
            val channel = edit(xy.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val x = ccX.text.toString().toIntOrNull()?.takeIf { it in Assignment.CC_RANGE }
                    val y = ccY.text.toString().toIntOrNull()?.takeIf { it in Assignment.CC_RANGE }
                    val ch = channel.text.toString().toIntOrNull()?.takeIf { it in Assignment.CHANNEL_RANGE }
                    ccX.error = if (x == null) getString(R.string.error_cc) else null
                    ccY.error = if (y == null) getString(R.string.error_cc) else null
                    channel.error = if (ch == null) getString(R.string.error_channel) else null
                    if (x != null && y != null && ch != null) store.saveXyAssignment(i, XyAssignment(name.text.toString(), x, y, ch))
                }
            }
            listOf(name, ccX, ccY, channel).forEach { it.addTextChangedListener(watcher) }
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(getString(R.string.xy_number, i + 1), Palette.TEXT, 16f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(ccX, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ccY, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(channel, LinearLayout.LayoutParams(dp(MODE_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT))
            })
        }
    }

    private fun columnLabels(kind: ControlKind?): View = LinearLayout(this).apply {
        addView(text("", Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_name), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(getString(R.string.label_cc), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_channel), Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        val last = when (kind) {
            ControlKind.FADER -> getString(R.string.label_max)
            ControlKind.BUTTON -> getString(R.string.label_mode)
            null -> ""
        }
        addView(text(last, Palette.TEXT_DIM, 13f), LinearLayout.LayoutParams(dp(MODE_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun row(kind: ControlKind, index: Int, a: Assignment, label: String, max: Int?): View {
        val name = edit(a.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
        val cc = edit(a.cc.toString(), InputType.TYPE_CLASS_NUMBER, 3)
        val channel = edit(a.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val mode = if (kind == ControlKind.BUTTON) modeSwitch(a.mode) else null
        val maxField = max?.let { edit(it.toString(), InputType.TYPE_CLASS_NUMBER, 3) }
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
        maxField?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val m = maxField.text.toString().toIntOrNull()?.takeIf { it in FaderMax.RANGE }
                maxField.error = if (m == null) getString(R.string.error_max) else null
                if (m != null) store.saveFaderMax(index, m)
            }
        })
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
            addView(
                maxField ?: mode ?: View(this@SettingsActivity),
                LinearLayout.LayoutParams(dp(MODE_WIDTH), if (mode != null) dp(36) else LinearLayout.LayoutParams.WRAP_CONTENT),
            )
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
            assignments[r.kind to r.index] = a
            store.save(r.kind, r.index, a)
            updateWarnings()
        }
    }

    private fun updateWarnings() {
        rows.forEach { r ->
            val a = r.current ?: return@forEach
            val others = assignments.entries
                .filter { (key, o) -> key != (r.kind to r.index) && o.cc == a.cc && o.channel == a.channel }
                .map { it.key }
                .sortedWith(compareBy({ it.first.ordinal }, { it.second }))
            if (others.isEmpty()) {
                r.warning.visibility = View.GONE
            } else {
                r.warning.text = getString(R.string.warning_duplicate, others.joinToString(", ") { label(it.first, it.second) })
                r.warning.visibility = View.VISIBLE
            }
        }
    }

    private fun label(kind: ControlKind, index: Int): String = getString(
        when (kind) {
            ControlKind.FADER -> R.string.fader_number
            ControlKind.BUTTON -> R.string.button_number
        },
        index + 1,
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAGE = "page"
        private const val STATE_PAGE = "page"
        private const val MODE_WIDTH = 110
    }
}
