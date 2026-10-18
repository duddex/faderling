package com.duddex.faderling

import android.app.Activity
import android.graphics.Color
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

    private class Row(val name: EditText, val cc: EditText, val channel: EditText, val warning: TextView)

    private lateinit var store: AssignmentStore
    private val current = mutableListOf<Assignment>()
    private val rows = mutableListOf<Row>()

    private val textColor = Color.rgb(0xEC, 0xEF, 0xF4)
    private val dimColor = Color.rgb(0xB0, 0xB6, 0xC2)
    private val warningColor = Color.rgb(0xFF, 0xA7, 0x26)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AssignmentStore(this)
        current += store.loadAll()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        root.addView(header())
        root.addView(columnLabels())

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        current.forEachIndexed { i, a -> list.addView(row(i, a)) }
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
        addView(text(getString(R.string.settings), textColor, 20f).apply { setPadding(dp(8), 0, 0, 0) })
    }

    private fun columnLabels(): View = LinearLayout(this).apply {
        setPadding(0, dp(4), 0, 0)
        addView(text("", dimColor, 13f), LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_name), dimColor, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(getString(R.string.label_cc), dimColor, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(getString(R.string.label_channel), dimColor, 13f), LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun row(index: Int, a: Assignment): View {
        val name = edit(a.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, Assignment.MAX_NAME_LENGTH)
        val cc = edit(a.cc.toString(), InputType.TYPE_CLASS_NUMBER, 3)
        val channel = edit(a.channel.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val warning = text("", warningColor, 13f).apply { visibility = View.GONE }
        val r = Row(name, cc, channel, warning)
        rows += r

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onRowEdited(index)
        }
        name.addTextChangedListener(watcher)
        cc.addTextChangedListener(watcher)
        channel.addTextChangedListener(watcher)

        val line = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(getString(R.string.fader_number, index + 1), textColor, 16f), LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(cc, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(channel, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(line)
            addView(warning, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(72) })
        }
    }

    private fun onRowEdited(index: Int) {
        val r = rows.getOrNull(index) ?: return
        val cc = r.cc.text.toString().toIntOrNull()?.takeIf { it in Assignment.CC_RANGE }
        val channel = r.channel.text.toString().toIntOrNull()?.takeIf { it in Assignment.CHANNEL_RANGE }
        r.cc.error = if (cc == null) getString(R.string.error_cc) else null
        r.channel.error = if (channel == null) getString(R.string.error_channel) else null
        if (cc != null && channel != null) {
            val a = Assignment(r.name.text.toString(), cc, channel)
            current[index] = a
            store.save(index, a)
            updateWarnings()
        }
    }

    private fun updateWarnings() {
        rows.forEachIndexed { i, r ->
            val others = current.indices.filter { j -> j != i && current[j].cc == current[i].cc && current[j].channel == current[i].channel }
            if (others.isEmpty()) {
                r.warning.visibility = View.GONE
            } else {
                r.warning.text = getString(R.string.warning_duplicate, others.joinToString(", ") { (it + 1).toString() })
                r.warning.visibility = View.VISIBLE
            }
        }
    }

    private fun edit(value: String, type: Int, maxLength: Int) = EditText(this).apply {
        setText(value)
        inputType = type
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NEXT
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        setSingleLine()
        setTextColor(textColor)
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
}
