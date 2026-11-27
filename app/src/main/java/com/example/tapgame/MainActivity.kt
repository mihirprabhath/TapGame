package com.example.tapgame

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {

    private enum class Page { PLAY, PROFILE, SETTINGS }

    private lateinit var gameView: GameView
    private lateinit var content: FrameLayout
    private lateinit var scrim: View
    private lateinit var drawer: LinearLayout
    private lateinit var drawerName: TextView
    private lateinit var drawerAvatar: TextView
    private lateinit var drawerSub: TextView
    private val navItems = mutableMapOf<Page, TextView>()

    private lateinit var prefs: android.content.SharedPreferences
    private var page = Page.PLAY
    private var drawerOpen = false

    private val bg = Color.parseColor("#1E1E2C")
    private val panel = Color.parseColor("#2A2A3D")
    private val accent = Color.parseColor("#40C4FF")
    private val muted = Color.parseColor("#9A9AB0")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("tapgame", Context.MODE_PRIVATE)
        gameView = GameView(this)

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        content = FrameLayout(this)
        root.addView(content, match())

        // hamburger button (top-left, above all pages)
        val menuBtn = TextView(this).apply {
            text = "☰"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circle(Color.parseColor("#55000000"))
            setOnClickListener { setDrawer(true) }
        }
        root.addView(menuBtn, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
            leftMargin = dp(12); topMargin = dp(28)
        })

        scrim = View(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { setDrawer(false) }
        }
        root.addView(scrim, match())

        drawer = buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT))
        drawer.translationX = -dp(280).toFloat()

        setContentView(root)
        showPage(Page.PLAY)
    }

    private fun match() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
    }

    private fun rounded(color: Int, r: Int = 12) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(color)
    }

    // ---------------- Drawer ----------------
    private fun buildDrawer(): LinearLayout {
        val d = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            isClickable = true // swallow touches
            elevation = dp(16).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(20))
            setBackgroundColor(Color.parseColor("#33415C"))
        }
        drawerAvatar = avatarView(64)
        header.addView(drawerAvatar, LinearLayout.LayoutParams(dp(64), dp(64)))
        drawerName = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, 0)
        }
        drawerSub = TextView(this).apply { setTextColor(muted); textSize = 14f }
        header.addView(drawerName)
        header.addView(drawerSub)
        d.addView(header)

        addNav(d, Page.PLAY, "🎮  Play")
        addNav(d, Page.PROFILE, "👤  Profile")
        addNav(d, Page.SETTINGS, "⚙  Settings")
        return d
    }

    private fun addNav(parent: LinearLayout, p: Page, label: String) {
        val tv = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener { showPage(p); setDrawer(false) }
        }
        navItems[p] = tv
        parent.addView(tv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(8); leftMargin = dp(8); rightMargin = dp(8) })
    }

    private fun setDrawer(open: Boolean) {
        drawerOpen = open
        if (open) {
            gameView.pause()
            refreshDrawerHeader()
            hideKeyboard()
            scrim.visibility = View.VISIBLE
        }
        scrim.animate().alpha(if (open) 1f else 0f).setDuration(200)
            .withEndAction { if (!drawerOpen) scrim.visibility = View.GONE }.start()
        drawer.animate().translationX(if (open) 0f else -dp(280).toFloat())
            .setDuration(220).start()
        if (!open && page == Page.PLAY) gameView.resume()
    }

    private fun refreshDrawerHeader() {
        val name = playerName()
        drawerName.text = name
        drawerAvatar.text = name.take(1).uppercase()
        drawerSub.text = "Level ${level()} · Best ${prefs.getInt("high", 0)}"
        for ((p, tv) in navItems) {
            tv.background = if (p == page) rounded(Color.parseColor("#3340C4FF")) else null
        }
    }

    // ---------------- Pages ----------------
    private fun showPage(p: Page) {
        page = p
        content.removeAllViews()
        when (p) {
            Page.PLAY -> {
                content.addView(gameView, match())
                gameView.reloadHighScore()
                gameView.resume()
            }
            Page.PROFILE -> {
                gameView.pause()
                content.addView(buildProfile(), match())
            }
            Page.SETTINGS -> {
                gameView.pause()
                content.addView(buildSettings(), match())
            }
        }
        refreshDrawerHeader()
    }

    private fun pageContainer(title: String): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        val titleView = TextView(this).apply {
            text = title; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(64), 0, 0, 0)
        }
        col.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val sv = ScrollView(this).apply { addView(col) }
        return sv to col
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }.also {
        // spacing handled by caller via layout params
    }

    private fun addCard(parent: LinearLayout, c: View) {
        parent.addView(c, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
    }

    private fun label(text: String, size: Float = 16f, color: Int = Color.WHITE) =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(color) }

    private fun avatarView(sizeDp: Int) = TextView(this).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = sizeDp * 0.4f
        typeface = Typeface.DEFAULT_BOLD
        background = circle(accent)
        text = playerName().take(1).uppercase()
    }

    // ---------------- Profile ----------------
    private fun playerName() = prefs.getString("name", "Player") ?: "Player"
    private fun level() = prefs.getInt("taps", 0) / 50 + 1

    private fun buildProfile(): View {
        val (sv, col) = pageContainer("Profile")

        val top = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val avatar = avatarView(88)
        top.addView(avatar, LinearLayout.LayoutParams(dp(88), dp(88)))
        val lvl = label("Level ${level()}", 14f, muted).apply { gravity = Gravity.CENTER }
        top.addView(lvl, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val nameInput = EditText(this).apply {
            setText(playerName())
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            hint = "Your name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(android.text.InputFilter.LengthFilter(16))
            gravity = Gravity.CENTER
            textSize = 20f
            maxLines = 1
        }
        top.addView(nameInput)
        val save = Button(this).apply {
            text = "Save name"
            setOnClickListener {
                val n = nameInput.text.toString().trim().ifEmpty { "Player" }
                prefs.edit().putString("name", n).apply()
                nameInput.setText(n)
                avatar.text = n.take(1).uppercase()
                hideKeyboard()
                refreshDrawerHeader()
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        top.addView(save)
        addCard(col, top)

        val stats = card()
        stats.addView(label("Stats", 18f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        statRow(stats, "Best score", prefs.getInt("high", 0).toString())
        statRow(stats, "Games played", prefs.getInt("games", 0).toString())
        statRow(stats, "Circles tapped", prefs.getInt("taps", 0).toString())
        val toNext = 50 - prefs.getInt("taps", 0) % 50
        statRow(stats, "Taps to next level", toNext.toString())
        addCard(col, stats)
        return sv
    }

    private fun statRow(parent: LinearLayout, k: String, v: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(label(k, 16f, muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label(v).apply { typeface = Typeface.DEFAULT_BOLD })
        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
    }

    // ---------------- Settings ----------------
    private fun buildSettings(): View {
        val (sv, col) = pageContainer("Settings")

        val diff = card()
        diff.addView(label("Difficulty", 18f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val names = listOf("Easy", "Normal", "Hard")
        val current = prefs.getInt("difficulty", 1)
        names.forEachIndexed { i, n ->
            group.addView(RadioButton(this).apply {
                id = 1000 + i
                text = n
                setTextColor(Color.WHITE)
                isChecked = i == current
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            prefs.edit().putInt("difficulty", id - 1000).apply()
        }
        diff.addView(group)
        addCard(col, diff)

        val fb = card()
        fb.addView(label("Feedback", 18f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        val sw = Switch(this).apply {
            text = "Vibration on tap / miss"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("haptics", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("haptics", on).apply() }
        }
        fb.addView(sw, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        addCard(col, fb)

        val data = card()
        data.addView(label("Data", 18f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        data.addView(Button(this).apply {
            text = "Reset stats & best score"
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Reset stats?")
                    .setMessage("This clears your best score, games played and level.")
                    .setPositiveButton("Reset") { _, _ ->
                        prefs.edit().putInt("high", 0).putInt("games", 0).putInt("taps", 0).apply()
                        gameView.reloadHighScore()
                        refreshDrawerHeader()
                        Toast.makeText(context, "Stats reset", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        addCard(col, data)
        return sv
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    // ---------------- Lifecycle ----------------
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> setDrawer(false)
            page != Page.PLAY -> showPage(Page.PLAY)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        if (page == Page.PLAY && !drawerOpen) gameView.resume()
    }
}
