package com.example.tapgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.random.Random

/**
 * TAP THE CIRCLES
 * - Circles appear and shrink. Tap them before they vanish!
 * - Missing a circle costs a life. 3 lives total.
 * - The game gets faster as your score goes up.
 *
 * Easy things to edit: the CONFIG values below, colors, and texts.
 */
class GameView(context: Context) : View(context) {

    // ---------- CONFIG (change these to tweak the game) ----------
    private val startingLives = 3
    private val maxRadius = 110f          // biggest size of a circle (px)
    private val baseLifetimeMs = 1800f    // how long a circle lives at the start
    private val minLifetimeMs = 600f      // fastest it can get
    private val baseSpawnMs = 900f        // time between circles at the start
    private val minSpawnMs = 350f         // fastest spawn rate
    private val circleColors = intArrayOf(
        Color.parseColor("#FF5252"), Color.parseColor("#FFAB40"),
        Color.parseColor("#FFD740"), Color.parseColor("#69F0AE"),
        Color.parseColor("#40C4FF"), Color.parseColor("#B388FF")
    )
    // --------------------------------------------------------------

    private class Target(
        val x: Float, val y: Float, val color: Int, val lifetime: Float
    ) {
        var age = 0f
    }

    private enum class State { READY, PLAYING, GAME_OVER }

    private val targets = mutableListOf<Target>()
    private var state = State.READY
    private var score = 0
    private var lives = startingLives
    private var highScore = 0
    private var spawnTimer = 0f
    private var lastFrameNs = 0L
    private var running = false

    private val prefs = context.getSharedPreferences("tapgame", Context.MODE_PRIVATE)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

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

    // ---------- Game logic ----------
    private fun startGame() {
        targets.clear()
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
        val lifetime = baseLifetimeMs - (baseLifetimeMs - minLifetimeMs) * difficulty()
        targets.add(Target(x, y, circleColors.random(), lifetime))
    }

    private fun update(dtMs: Float) {
        if (state != State.PLAYING) return

        spawnTimer -= dtMs
        if (spawnTimer <= 0f) {
            spawnTarget()
            spawnTimer = baseSpawnMs - (baseSpawnMs - minSpawnMs) * difficulty()
        }

        val it = targets.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.age += dtMs
            if (t.age >= t.lifetime) {   // missed it
                it.remove()
                lives--
                if (lives <= 0) endGame()
            }
        }
    }

    private fun endGame() {
        state = State.GAME_OVER
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high", highScore).apply()
        }
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
                    if (hypot(event.x - t.x, event.y - t.y) <= radiusOf(t) + 12f) {
                        targets.removeAt(i)
                        score++
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

        // circles
        for (t in targets) {
            circlePaint.color = t.color
            canvas.drawCircle(t.x, t.y, radiusOf(t), circlePaint)
        }

        // HUD
        textPaint.textSize = 56f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: $score", 40f, 100f, textPaint)
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
            }
            State.GAME_OVER -> {
                textPaint.textSize = 90f
                canvas.drawText("GAME OVER", width / 2f, height / 2f - 100f, textPaint)
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
}
