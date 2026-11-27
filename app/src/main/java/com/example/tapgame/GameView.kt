package com.example.tapgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * TAP THE CIRCLES
 * - Circles appear and shrink. Tap them before they vanish (they get sliced!).
 * - Missing a circle costs a life. 3 lives total.
 * - Gold speed bubbles are quick but worth 5 points (missing them is free).
 * - Tapping a BOMB ends the game instantly. Let bombs fade away.
 * - The game gets faster as your score goes up.
 */
class GameView(context: Context) : View(context) {

    // ---------- CONFIG (change these to tweak the game) ----------
    private val startingLives = 3
    private val maxRadius = 110f          // biggest size of a circle (px)
    private val baseLifetimeMs = 1800f    // how long a circle lives at the start
    private val minLifetimeMs = 600f      // fastest it can get
    private val baseSpawnMs = 900f        // time between circles at the start
    private val minSpawnMs = 350f         // fastest spawn rate
    private val fastPoints = 5            // points for a gold speed bubble
    private val fastLifeFactor = 0.45f    // speed bubbles live this fraction of normal
    private val bombChance = 0.15f        // after score >= 3
    private val fastChance = 0.15f        // after score >= 2
    private val circleColors = intArrayOf(
        Color.parseColor("#FF5252"), Color.parseColor("#FFAB40"),
        Color.parseColor("#FFD740"), Color.parseColor("#69F0AE"),
        Color.parseColor("#40C4FF"), Color.parseColor("#B388FF")
    )
    // --------------------------------------------------------------

    private enum class Kind { NORMAL, FAST, BOMB }

    private class Target(
        val x: Float, val y: Float, val color: Int, val lifetime: Float, val kind: Kind
    ) {
        var age = 0f
    }

    /** A sliced bubble: two halves flying apart plus a slash line. */
    private class Slice(val x: Float, val y: Float, val r: Float, val color: Int, val angle: Float) {
        var age = 0f
    }

    private class Popup(val x: Float, val y: Float, val text: String) {
        var age = 0f
    }

    private class Blast(val x: Float, val y: Float) {
        var age = 0f
    }

    private enum class State { READY, PLAYING, GAME_OVER }

    private val sliceMs = 400f
    private val popupMs = 600f
    private val blastMs = 600f
    private val goldColor = Color.parseColor("#FFD700")

    private val targets = mutableListOf<Target>()
    private val slices = mutableListOf<Slice>()
    private val popups = mutableListOf<Popup>()
    private val blasts = mutableListOf<Blast>()
    private var state = State.READY
    private var score = 0
    private var lives = startingLives
    private var highScore = 0
    private var spawnTimer = 0f
    private var lastFrameNs = 0L
    private var running = false
    private var bombed = false
    private var speedScale = 1f           // >1 = more forgiving (Easy), <1 = harder

    private val prefs = context.getSharedPreferences("tapgame", Context.MODE_PRIVATE)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()

    init {
        setBackgroundColor(Color.parseColor("#1E1E2C"))
        highScore = prefs.getInt("high", 0)
    }

    // ---------- Lifecycle ----------
    fun resume() {
        running = true
        lastFrameNs = System.nanoTime()
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
    }

    fun reloadHighScore() {
        highScore = prefs.getInt("high", 0)
        invalidate()
    }

    private fun haptic() {
        if (prefs.getBoolean("haptics", true)) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // ---------- Game logic ----------
    private fun startGame() {
        // Difficulty setting: 0 = Easy, 1 = Normal, 2 = Hard
        speedScale = when (prefs.getInt("difficulty", 1)) {
            0 -> 1.3f
            2 -> 0.75f
            else -> 1f
        }
        targets.clear()
        slices.clear()
        popups.clear()
        blasts.clear()
        bombed = false
        score = 0
        lives = startingLives
        spawnTimer = 0f
        state = State.PLAYING
    }

    private fun difficulty(): Float = (score / 40f).coerceIn(0f, 1f) // 0..1

    private fun spawnTarget() {
        val margin = maxRadius + 10f
        if (width <= 2 * margin || height <= 2 * margin + 200f) return
        val x = Random.nextFloat() * (width - 2 * margin) + margin
        val y = Random.nextFloat() * (height - 2 * margin - 200f) + margin + 160f
        val lifetime =
            (baseLifetimeMs - (baseLifetimeMs - minLifetimeMs) * difficulty()) * speedScale
        val roll = Random.nextFloat()
        val t = when {
            score >= 3 && roll < bombChance ->
                Target(x, y, Color.parseColor("#2B2B3A"), lifetime, Kind.BOMB)
            score >= 2 && roll < bombChance + fastChance ->
                Target(x, y, goldColor, lifetime * fastLifeFactor, Kind.FAST)
            else -> Target(x, y, circleColors.random(), lifetime, Kind.NORMAL)
        }
        targets.add(t)
    }

    private fun update(dtMs: Float) {
        // effects keep animating even after the game ends
        slices.forEach { it.age += dtMs }; slices.removeAll { it.age >= sliceMs }
        popups.forEach { it.age += dtMs }; popups.removeAll { it.age >= popupMs }
        blasts.forEach { it.age += dtMs }; blasts.removeAll { it.age >= blastMs }

        if (state != State.PLAYING) return

        spawnTimer -= dtMs
        if (spawnTimer <= 0f) {
            spawnTarget()
            spawnTimer = (baseSpawnMs - (baseSpawnMs - minSpawnMs) * difficulty()) * speedScale
        }

        val it = targets.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.age += dtMs
            if (t.age >= t.lifetime) {
                it.remove()
                // bombs and speed bubbles can be ignored safely
                if (t.kind == Kind.NORMAL) {
                    lives--
                    haptic()
                    if (lives <= 0) endGame()
                }
            }
        }
    }

    private fun endGame() {
        state = State.GAME_OVER
        if (score > highScore) highScore = score
        prefs.edit()
            .putInt("high", highScore)
            .putInt("games", prefs.getInt("games", 0) + 1)
            .putInt("taps", prefs.getInt("taps", 0) + score)
            .apply()
    }

    private fun radiusOf(t: Target): Float {
        val progress = t.age / t.lifetime            // 0 -> 1
        return maxRadius * (1f - progress * 0.85f)   // shrinks to 15%
    }

    // ---------- Input ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        when (state) {
            State.READY, State.GAME_OVER -> startGame()
            State.PLAYING -> {
                // check newest circles first
                for (i in targets.indices.reversed()) {
                    val t = targets[i]
                    val r = radiusOf(t)
                    if (hypot(event.x - t.x, event.y - t.y) <= r + 12f) {
                        targets.removeAt(i)
                        haptic()
                        if (t.kind == Kind.BOMB) {
                            blasts.add(Blast(t.x, t.y))
                            bombed = true
                            endGame()
                        } else {
                            val pts = if (t.kind == Kind.FAST) fastPoints else 1
                            score += pts
                            slices.add(Slice(t.x, t.y, r, t.color, Random.nextFloat() * 180f - 90f))
                            popups.add(Popup(t.x, t.y - r, "+$pts"))
                        }
                        break
                    }
                }
            }
        }
        return true
    }

    // ---------- Drawing ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        val dtMs = ((now - lastFrameNs) / 1_000_000f).coerceAtMost(50f)
        lastFrameNs = now
        update(dtMs)

        for (t in targets) drawTarget(canvas, t)
        for (s in slices) drawSlice(canvas, s)
        for (b in blasts) drawBlast(canvas, b)
        for (p in popups) {
            val k = p.age / popupMs
            textPaint.color = Color.argb((255 * (1 - k)).toInt(), 255, 255, 255)
            textPaint.textSize = 52f
            canvas.drawText(p.text, p.x, p.y - 80f * k, textPaint)
        }
        textPaint.color = Color.WHITE

        // HUD
        textPaint.textSize = 56f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: $score", 100f, 100f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("♥".repeat(lives.coerceAtLeast(0)), width - 40f, 100f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER

        when (state) {
            State.READY -> {
                textPaint.textSize = 90f
                canvas.drawText("TAP CIRCLES", width / 2f, height / 2f - 80f, textPaint)
                textPaint.textSize = 48f
                canvas.drawText("Tap to start", width / 2f, height / 2f + 20f, textPaint)
                canvas.drawText("Best: $highScore", width / 2f, height / 2f + 100f, textPaint)
                textPaint.textSize = 36f
                textPaint.color = Color.parseColor("#9A9AB0")
                canvas.drawText("Gold = +$fastPoints   Bomb = game over!", width / 2f, height / 2f + 180f, textPaint)
                textPaint.color = Color.WHITE
            }
            State.GAME_OVER -> {
                textPaint.textSize = 90f
                canvas.drawText(if (bombed) "BOOM!" else "GAME OVER", width / 2f, height / 2f - 100f, textPaint)
                textPaint.textSize = 56f
                canvas.drawText("Score: $score", width / 2f, height / 2f, textPaint)
                canvas.drawText("Best: $highScore", width / 2f, height / 2f + 80f, textPaint)
                textPaint.textSize = 44f
                canvas.drawText("Tap to play again", width / 2f, height / 2f + 180f, textPaint)
            }
            else -> {}
        }

        if (running) postInvalidateOnAnimation()
    }

    private fun drawTarget(canvas: Canvas, t: Target) {
        val r = radiusOf(t)
        circlePaint.color = t.color
        canvas.drawCircle(t.x, t.y, r, circlePaint)
        when (t.kind) {
            Kind.BOMB -> {
                circlePaint.color = Color.parseColor("#55FFFFFF")
                canvas.drawCircle(t.x - r * 0.3f, t.y - r * 0.3f, r * 0.2f, circlePaint)
                ringPaint.color = Color.parseColor("#FF5252")
                ringPaint.strokeWidth = 6f
                canvas.drawCircle(t.x, t.y, r, ringPaint)
                // fuse + blinking spark
                ringPaint.color = Color.parseColor("#BBBBBB")
                ringPaint.strokeWidth = 8f
                val sx = t.x + r * 0.75f
                val sy = t.y - r * 1.05f
                canvas.drawLine(t.x + r * 0.4f, t.y - r * 0.8f, sx, sy, ringPaint)
                circlePaint.color =
                    if ((t.age / 100).toInt() % 2 == 0) Color.YELLOW else Color.parseColor("#FF9100")
                canvas.drawCircle(sx, sy, maxOf(r * 0.14f, 6f), circlePaint)
            }
            Kind.FAST -> {
                ringPaint.color = Color.WHITE
                ringPaint.strokeWidth = 6f
                canvas.drawCircle(t.x, t.y, r * 0.8f, ringPaint)
                textPaint.color = Color.parseColor("#5D4037")
                textPaint.textSize = r * 0.7f
                canvas.drawText("+$fastPoints", t.x, t.y + r * 0.25f, textPaint)
                textPaint.color = Color.WHITE
            }
            Kind.NORMAL -> {}
        }
    }

    private fun drawSlice(canvas: Canvas, s: Slice) {
        val k = s.age / sliceMs            // 0..1
        val travel = 70f * k
        val fall = 120f * k * k
        // each half drifts away perpendicular to the cut, then falls and fades
        for (half in 0..1) {
            val start = s.angle + 180f * half
            val dir = Math.toRadians(start + 90.0)
            val cx = s.x + cos(dir).toFloat() * travel
            val cy = s.y + sin(dir).toFloat() * travel + fall
            arcRect.set(cx - s.r, cy - s.r, cx + s.r, cy + s.r)
            circlePaint.color = s.color
            circlePaint.alpha = (255 * (1 - k)).toInt()
            canvas.drawArc(arcRect, start, 180f, true, circlePaint)
        }
        circlePaint.alpha = 255
        // quick white slash line across the bubble
        if (k < 0.4f) {
            val a = Math.toRadians(s.angle.toDouble())
            val len = s.r * (1.4f + k)
            ringPaint.color = Color.argb((255 * (1 - k / 0.4f)).toInt(), 255, 255, 255)
            ringPaint.strokeWidth = 8f * (1 - k)
            canvas.drawLine(
                s.x - cos(a).toFloat() * len, s.y - sin(a).toFloat() * len,
                s.x + cos(a).toFloat() * len, s.y + sin(a).toFloat() * len, ringPaint
            )
        }
    }

    private fun drawBlast(canvas: Canvas, b: Blast) {
        val k = b.age / blastMs
        circlePaint.color = Color.argb((220 * (1 - k)).toInt(), 255, 140, 0)
        canvas.drawCircle(b.x, b.y, 60f + 260f * k, circlePaint)
        circlePaint.color = Color.argb((255 * (1 - k)).toInt(), 255, 235, 120)
        canvas.drawCircle(b.x, b.y, 30f + 140f * k, circlePaint)
    }
}
