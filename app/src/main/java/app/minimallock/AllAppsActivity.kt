package app.minimallock

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Small, quiet text list of all apps with search. Enter opens the first match. */
class AllAppsActivity : Activity() {

    private var all: List<Apps.App> = emptyList()
    private var shown: List<Apps.App> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        all = Apps.launchable(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 48), Ui.dp(this, 28), Ui.dp(this, 8))

        val et = EditText(this)
        et.hint = "search"
        et.setSingleLine(true)
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Ui.GRAY)
        et.textSize = 16f
        et.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        et.imeOptions = EditorInfo.IME_ACTION_GO
        root.addView(et)

        val lv = ListView(this)
        lv.divider = null
        lv.setSelector(android.R.color.transparent)
        val pad = Ui.dp(this, 11)
        adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            ArrayList<String>()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.textSize = 17f
                v.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                v.minHeight = 0
                v.setPadding(0, pad, 0, pad)
                return v
            }
        }
        lv.adapter = adapter
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        lv.setOnItemClickListener { _, _, pos, _ -> open(shown[pos]) }
        et.setOnEditorActionListener { _, _, _ ->
            val first = shown.firstOrNull()
            if (first != null) open(first)
            true
        }
        et.onChange { update(et.text.toString()) }

        setContentView(root)
        update("")
    }

    private fun update(q: String) {
        val s = q.trim().lowercase()
        shown = if (s.isEmpty()) all else all.filter { it.label.lowercase().contains(s) }
        adapter.clear()
        adapter.addAll(shown.map { it.label })
        adapter.notifyDataSetChanged()
    }

    private fun open(a: Apps.App) {
        Apps.launch(this, a.pkg)
        finish()
    }
}
