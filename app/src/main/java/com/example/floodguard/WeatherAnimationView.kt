package com.example.floodguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-performance hardware-accelerated animated weather background for the forecast card.
 * Dynamically renders live atmospheric effects:
 * - THUNDERSTORM: Heavy slanted rainfall with realistic electric lightning flashes and stormy dark gradient.
 * - RAIN / SHOWERS: Smooth rhythmic raindrops falling across cool navy slate atmosphere.
 * - DRIZZLE: Soft, misty droplets with gentle vertical drift.
 * - CLOUDY / FOGGY: Layered atmospheric cloud puffs softly drifting horizontally.
 * - CLEAR / SUNNY: Warm glowing radiant sun orb with pulsating concentric halos and floating light motes.
 */
class WeatherAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Condition {
        THUNDERSTORM,
        RAIN,
        DRIZZLE,
        CLOUDY,
        CLEAR
    }

    private var currentCondition = Condition.THUNDERSTORM
    private var isAnimating = true
    private var lastFrameTime = 0L

    // Clipping path for card rounded corners (16dp)
    private val clipPath = Path()
    private val rectF = RectF()
    private val cornerRadius = 16f * resources.displayMetrics.density

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val lightningGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val flashOverlayPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Rain Particles
    private class Drop(
        var x: Float = 0f,
        var y: Float = 0f,
        var length: Float = 0f,
        var speed: Float = 0f,
        var alpha: Int = 120,
        var width: Float = 2f
    )
    private val rainDrops = ArrayList<Drop>()

    // Lightning Flash State
    private var nextLightningTime = 0L
    private var flashAlpha = 0f
    private val lightningPath = Path()
    private var showBolt = false

    // Cloud Elements
    private class Cloud(
        var x: Float = 0f,
        var y: Float = 0f,
        var radius: Float = 0f,
        var speed: Float = 0f,
        var alpha: Int = 40
    )
    private val clouds = ArrayList<Cloud>()

    // Sun & Mote Elements
    private var sunPulseAngle = 0f
    private class Mote(
        var x: Float = 0f,
        var y: Float = 0f,
        var radius: Float = 0f,
        var speedY: Float = 0f,
        var alpha: Int = 80
    )
    private val motes = ArrayList<Mote>()

    init {
        initParticles()
    }

    private fun initParticles() {
        val density = resources.displayMetrics.density
        val rnd = Random(System.currentTimeMillis())

        // Create 45 raindrops
        rainDrops.clear()
        for (i in 0 until 45) {
            rainDrops.add(
                Drop(
                    x = rnd.nextFloat() * 1000f,
                    y = rnd.nextFloat() * 800f,
                    length = (14f + rnd.nextFloat() * 16f) * density,
                    speed = (420f + rnd.nextFloat() * 480f) * density,
                    alpha = 60 + rnd.nextInt(120),
                    width = (1.2f + rnd.nextFloat() * 1.6f) * density
                )
            )
        }

        // Create 5 soft clouds
        clouds.clear()
        clouds.add(Cloud(50f, 40f, 65f * density, 8f * density, 45))
        clouds.add(Cloud(180f, 75f, 85f * density, 11f * density, 35))
        clouds.add(Cloud(300f, 35f, 60f * density, 9f * density, 50))
        clouds.add(Cloud(420f, 80f, 75f * density, 12f * density, 30))
        clouds.add(Cloud(540f, 45f, 70f * density, 7f * density, 40))

        // Create 15 floating light motes for Clear/Sunny
        motes.clear()
        for (i in 0 until 15) {
            motes.add(
                Mote(
                    x = rnd.nextFloat() * 1000f,
                    y = rnd.nextFloat() * 500f,
                    radius = (2f + rnd.nextFloat() * 3f) * density,
                    speedY = (10f + rnd.nextFloat() * 18f) * density,
                    alpha = 50 + rnd.nextInt(90)
                )
            )
        }

        scheduleNextLightning()
    }

    private fun scheduleNextLightning() {
        val delay = 2500L + Random.nextLong(4000L)
        nextLightningTime = SystemClock.uptimeMillis() + delay
    }

    fun setCondition(condition: Condition) {
        if (currentCondition != condition) {
            currentCondition = condition
            updateShader(width, height)
            invalidate()
        }
    }

    fun pauseAnimation() {
        isAnimating = false
    }

    fun resumeAnimation() {
        if (!isAnimating) {
            isAnimating = true
            lastFrameTime = 0L
            postInvalidateOnAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAnimating = true
        lastFrameTime = 0L
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        isAnimating = (visibility == VISIBLE)
        if (isAnimating) {
            lastFrameTime = 0L
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
        updateShader(w, h)

        // Spread raindrops across actual width
        val rnd = Random(42)
        for (drop in rainDrops) {
            drop.x = rnd.nextFloat() * (w + 100f)
            drop.y = rnd.nextFloat() * h.toFloat()
        }
    }

    private fun updateShader(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val colors = when (currentCondition) {
            Condition.THUNDERSTORM -> intArrayOf(
                Color.parseColor("#090D1A"),
                Color.parseColor("#171D33"),
                Color.parseColor("#0F1424")
            )
            Condition.RAIN -> intArrayOf(
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E293B"),
                Color.parseColor("#0F172A")
            )
            Condition.DRIZZLE -> intArrayOf(
                Color.parseColor("#131D31"),
                Color.parseColor("#1F2D44"),
                Color.parseColor("#152033")
            )
            Condition.CLOUDY -> intArrayOf(
                Color.parseColor("#1E293B"),
                Color.parseColor("#334155"),
                Color.parseColor("#1E293B")
            )
            Condition.CLEAR -> intArrayOf(
                Color.parseColor("#0F2942"),
                Color.parseColor("#1E3A8A"),
                Color.parseColor("#0B1D33")
            )
        }
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            colors, null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1000f).coerceIn(0.001f, 0.05f)
        lastFrameTime = now

        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Draw dynamic background gradient
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Render weather-specific particle animation
        when (currentCondition) {
            Condition.THUNDERSTORM -> {
                drawRain(canvas, w, h, dt, isHeavy = true)
                drawLightning(canvas, w, h, now, dt)
            }
            Condition.RAIN -> {
                drawRain(canvas, w, h, dt, isHeavy = false)
            }
            Condition.DRIZZLE -> {
                drawDrizzle(canvas, w, h, dt)
            }
            Condition.CLOUDY -> {
                drawClouds(canvas, w, h, dt)
            }
            Condition.CLEAR -> {
                drawSunAndMotes(canvas, w, h, dt)
            }
        }

        canvas.restore()

        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, dt: Float, isHeavy: Boolean) {
        val slant = if (isHeavy) 0.22f else 0.12f
        val count = if (isHeavy) rainDrops.size else (rainDrops.size * 0.7f).toInt()

        for (i in 0 until count) {
            val drop = rainDrops[i]
            drop.y += drop.speed * dt
            drop.x += drop.speed * slant * dt

            if (drop.y > h + drop.length) {
                drop.y = -drop.length
                drop.x = Random.nextFloat() * (w + 120f) - 60f
            }

            rainPaint.color = Color.argb(drop.alpha, 165, 218, 255)
            rainPaint.strokeWidth = drop.width

            val endX = drop.x + drop.length * slant
            val endY = drop.y + drop.length
            canvas.drawLine(drop.x, drop.y, endX, endY, rainPaint)
        }
    }

    private fun drawDrizzle(canvas: Canvas, w: Float, h: Float, dt: Float) {
        val count = (rainDrops.size * 0.5f).toInt()
        for (i in 0 until count) {
            val drop = rainDrops[i]
            drop.y += (drop.speed * 0.55f) * dt
            drop.x += (drop.speed * 0.05f) * dt

            if (drop.y > h + drop.length) {
                drop.y = -drop.length
                drop.x = Random.nextFloat() * w
            }

            rainPaint.color = Color.argb((drop.alpha * 0.6f).toInt(), 186, 230, 253)
            rainPaint.strokeWidth = drop.width * 0.7f
            canvas.drawLine(drop.x, drop.y, drop.x + drop.length * 0.05f, drop.y + drop.length * 0.65f, rainPaint)
        }
    }

    private fun drawLightning(canvas: Canvas, w: Float, h: Float, now: Long, dt: Float) {
        if (now >= nextLightningTime) {
            flashAlpha = 0.72f
            showBolt = true
            scheduleNextLightning()

            // Generate jagged lightning bolt
            lightningPath.reset()
            var lx = w * (0.35f + Random.nextFloat() * 0.45f)
            var ly = 0f
            lightningPath.moveTo(lx, ly)
            while (ly < h * 0.85f) {
                ly += (12f + Random.nextFloat() * 18f) * resources.displayMetrics.density
                lx += (-14f + Random.nextFloat() * 28f) * resources.displayMetrics.density
                lightningPath.lineTo(lx, ly)
            }
        }

        if (flashAlpha > 0f) {
            // Flash ambient overlay
            flashOverlayPaint.color = Color.argb((flashAlpha * 255).toInt(), 224, 242, 254)
            canvas.drawRect(0f, 0f, w, h, flashOverlayPaint)

            if (showBolt) {
                // Outer glow
                lightningGlowPaint.color = Color.argb((flashAlpha * 140).toInt(), 56, 189, 248)
                lightningGlowPaint.strokeWidth = 6f * resources.displayMetrics.density
                canvas.drawPath(lightningPath, lightningGlowPaint)

                // Inner core
                lightningPaint.color = Color.argb((flashAlpha * 255).toInt(), 255, 255, 255)
                lightningPaint.strokeWidth = 2.2f * resources.displayMetrics.density
                canvas.drawPath(lightningPath, lightningPaint)
            }

            flashAlpha -= dt * 4.5f
            if (flashAlpha <= 0f) {
                flashAlpha = 0f
                showBolt = false
            }
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, dt: Float) {
        for (cloud in clouds) {
            cloud.x += cloud.speed * dt
            if (cloud.x - cloud.radius > w) {
                cloud.x = -cloud.radius
            }

            cloudPaint.color = Color.argb(cloud.alpha, 148, 163, 184)
            canvas.drawCircle(cloud.x, cloud.y, cloud.radius, cloudPaint)
            // Nested puff for natural cloud fluff
            canvas.drawCircle(cloud.x + cloud.radius * 0.45f, cloud.y - cloud.radius * 0.2f, cloud.radius * 0.75f, cloudPaint)
            canvas.drawCircle(cloud.x - cloud.radius * 0.4f, cloud.y + cloud.radius * 0.1f, cloud.radius * 0.65f, cloudPaint)
        }
    }

    private fun drawSunAndMotes(canvas: Canvas, w: Float, h: Float, dt: Float) {
        sunPulseAngle += dt * 1.5f
        val pulse = 1f + 0.08f * sin(sunPulseAngle)

        val sunX = w * 0.82f
        val sunY = h * 0.35f
        val baseRadius = 40f * resources.displayMetrics.density

        // Outer radiant aura
        sunPaint.shader = RadialGradient(
            sunX, sunY, baseRadius * 2.2f * pulse,
            intArrayOf(
                Color.argb(90, 253, 224, 71),
                Color.argb(35, 245, 158, 11),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, baseRadius * 2.2f * pulse, sunPaint)

        // Inner glowing core
        sunPaint.shader = RadialGradient(
            sunX, sunY, baseRadius * 0.9f,
            intArrayOf(
                Color.argb(230, 254, 240, 138),
                Color.argb(160, 250, 204, 21),
                Color.argb(40, 245, 158, 11)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, baseRadius * 0.9f, sunPaint)

        // Floating sunbeam light motes
        for (mote in motes) {
            mote.y -= mote.speedY * dt
            if (mote.y < -mote.radius) {
                mote.y = h + mote.radius
                mote.x = Random.nextFloat() * w
            }
            motePaint.color = Color.argb(mote.alpha, 254, 240, 138)
            canvas.drawCircle(mote.x, mote.y, mote.radius, motePaint)
        }
    }
}
