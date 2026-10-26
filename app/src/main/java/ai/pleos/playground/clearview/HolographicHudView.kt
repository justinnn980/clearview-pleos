package ai.pleos.playground.clearview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Movie-grade futuristic Holographic Vehicle HUD rendered natively at 60fps.
 * Combines the studio side-profile sedan with:
 * - Sweeping cyan LiDAR scan beam
 * - Glowing glass contour highlights (BlurMaskFilter bloom)
 * - Rotating dual-ring target reticles (◎) locked onto Front Outer, Front Inner, Side Mirror, and Rear Glass
 * - 45-degree precision 1px leader lines & chamfered glassmorphic HUD callout nodes
 * - Floating smoked-glass bottom HUD console bar (1-line Notification + Futuristic Apply Actuator)
 */
class HolographicHudView(
    context: Context,
    private val onApplyCare: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    private val onRestoreCare: () -> Unit
) : View(context) {

    var frontOut: Boolean = true
    var frontIn: Boolean = true
    var sideMirror: Boolean = true
    var rearGlass: Boolean = true
    var isApplying: Boolean = false

    private val carBitmap: Bitmap? = try {
        context.assets.open("car_side_profile.jpg").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Throwable) {
        null
    }

    private var phase: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val path = Path()
    private val rect = RectF()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val nodeFrontOut = RectF()
    private val nodeFrontIn = RectF()
    private val nodeMirror = RectF()
    private val nodeRear = RectF()
    private val applyBtnRect = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            when {
                applyBtnRect.contains(x, y) -> {
                    handleApplyButton()
                    invalidate()
                }
                nodeFrontOut.contains(x, y) -> {
                    frontOut = !frontOut
                    isApplying = false
                    invalidate()
                }
                nodeFrontIn.contains(x, y) -> {
                    frontIn = !frontIn
                    isApplying = false
                    invalidate()
                }
                nodeMirror.contains(x, y) -> {
                    sideMirror = !sideMirror
                    isApplying = false
                    invalidate()
                }
                nodeRear.contains(x, y) -> {
                    rearGlass = !rearGlass
                    isApplying = false
                    invalidate()
                }
            }
        }
        return true
    }

    private fun handleApplyButton() {
        val anySelected = frontOut || frontIn || sideMirror || rearGlass
        if (!anySelected) {
            frontOut = true
            frontIn = true
            sideMirror = true
            rearGlass = true
            isApplying = false
            return
        }
        if (!isApplying) {
            isApplying = true
            onApplyCare(frontIn, frontOut, sideMirror, rearGlass)
        } else {
            isApplying = false
            frontOut = false
            frontIn = false
            sideMirror = false
            rearGlass = false
            onRestoreCare()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Full-bleed deep obsidian background (#040811)
        canvas.drawColor(Color.parseColor("#040811"))

        val padH = dp(18f)
        val padTop = dp(12f)
        val padBot = dp(12f)
        val barH = dp(62f)
        val stageGap = dp(10f)

        val stageRect = RectF(padH, padTop, w - padH, h - padBot - barH - stageGap)
        val consoleRect = RectF(padH, h - padBot - barH, w - padH, h - padBot)

        // 1. Draw Upper Holographic Vehicle Stage
        drawHolographicStage(canvas, stageRect)

        // 2 & 3. Draw Bottom Smoked-Glass Console Bar (Notification + Apply Button)
        drawBottomConsoleBar(canvas, consoleRect)
    }

    private fun drawHolographicStage(canvas: Canvas, stage: RectF) {
        canvas.save()
        path.reset()
        path.addRoundRect(stage, dp(20f), dp(20f), Path.Direction.CW)
        canvas.clipPath(path)

        val sw = stage.width()
        val sh = stage.height()

        // 1A. Draw the studio white sedan image (center-crop preserving 1376x768 aspect ratio)
        val bmp = carBitmap
        var imgLeft = stage.left
        var imgTop = stage.top
        var imgW = sw
        var imgH = sh

        if (bmp != null) {
            val scale = maxOf(sw / bmp.width.toFloat(), sh / bmp.height.toFloat())
            imgW = bmp.width * scale
            imgH = bmp.height * scale
            imgLeft = stage.centerX() - imgW * 0.5f
            imgTop = stage.centerY() - imgH * 0.52f
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(imgLeft, imgTop, imgLeft + imgW, imgTop + imgH)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }

        // Helper to map (x, y) in 1376x768 image space to screen coordinates!
        fun mapX(px: Float): Float = imgLeft + (px / 1376f) * imgW
        fun mapY(py: Float): Float = imgTop + (py / 768f) * imgH

        // 1B. Subtle sci-fi telemetry grid overlay
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.6f)
        paint.color = Color.argb(14, 56, 189, 248)
        val gridStep = dp(38f)
        var gx = stage.left
        while (gx < stage.right) {
            canvas.drawLine(gx, stage.top, gx, stage.bottom, paint)
            gx += gridStep
        }
        var gy = stage.top
        while (gy < stage.bottom) {
            canvas.drawLine(stage.left, gy, stage.right, gy, paint)
            gy += gridStep
        }

        // 1C. Deep obsidian edge vignette
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            stage.centerX(),
            stage.centerY(),
            sw * 0.62f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(110, 4, 8, 17),
                Color.argb(230, 4, 8, 17)
            ),
            floatArrayOf(0.45f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(stage, paint)
        paint.shader = null

        // 1D. Sweeping Holographic LiDAR Scan Beam
        val scanX = stage.left - dp(80f) + (sw + dp(160f)) * phase
        paint.shader = LinearGradient(
            scanX - dp(50f), 0f, scanX + dp(50f), 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(38, 0, 240, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(scanX - dp(50f), stage.top, scanX + dp(50f), stage.bottom, paint)
        paint.shader = null

        // 1E. Glowing Glass Contour Highlights right on the car's windshield & rear glass
        // Front Outer Windshield Slope: (442,332) -> (606,236)
        if (frontOut) {
            val col = if (isApplying) Color.parseColor("#00F0FF") else Color.parseColor("#FFB800")
            drawGlowingGlassLine(canvas, mapX(442f), mapY(332f), mapX(606f), mapY(236f), col)
        }

        // Front Inner Windshield Cabin Area: (480,330) -> (615,244) -> (690,244) -> (685,330)
        if (frontIn) {
            val col = if (isApplying) Color.parseColor("#00F0FF") else Color.parseColor("#38BDF8")
            path.reset()
            path.moveTo(mapX(480f), mapY(330f))
            path.lineTo(mapX(615f), mapY(244f))
            path.lineTo(mapX(690f), mapY(244f))
            path.lineTo(mapX(685f), mapY(330f))
            path.close()
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (isApplying) 55 else 38, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawPath(path, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.5f)
            paint.color = col
            canvas.drawPath(path, paint)
        }

        // Rear Windshield Slope: (915,244) -> (1098,308)
        if (rearGlass) {
            val col = if (isApplying) Color.parseColor("#00F0FF") else Color.parseColor("#FFB800")
            drawGlowingGlassLine(canvas, mapX(915f), mapY(244f), mapX(1098f), mapY(308f), col)
        }

        // 1F. Exact Target Coordinates on the Car
        val txFrontOut = mapX(498f)
        val tyFrontOut = mapY(292f)

        val txFrontIn = mapX(625f)
        val tyFrontIn = mapY(286f)

        val txMirror = mapX(546f)
        val tyMirror = mapY(325f)

        val txRear = mapX(1010f)
        val tyRear = mapY(274f)

        // 1G. Position the 4 Chamfered Holographic HUD Callout Cards
        val cardW = sw * 0.255f
        val cardH = dp(56f)

        nodeFrontOut.set(stage.left + dp(16f), stage.top + dp(14f), stage.left + dp(16f) + cardW, stage.top + dp(14f) + cardH)
        nodeFrontIn.set(stage.left + dp(26f) + cardW, stage.top + dp(14f), stage.left + dp(26f) + cardW * 2f, stage.top + dp(14f) + cardH)
        nodeRear.set(stage.right - dp(16f) - cardW, stage.top + dp(14f), stage.right - dp(16f), stage.top + dp(14f) + cardH)
        nodeMirror.set(stage.left + dp(16f), stage.bottom - dp(14f) - cardH, stage.left + dp(16f) + cardW * 1.06f, stage.bottom - dp(14f))

        // Draw 45-degree Sci-Fi Leader Lines, Rotating Reticles & HUD Cards
        drawSciFiNode(
            canvas = canvas,
            card = nodeFrontOut,
            elbowX = txFrontOut - dp(34f),
            elbowY = nodeFrontOut.bottom + dp(14f),
            targetX = txFrontOut,
            targetY = tyFrontOut,
            fromBottom = true,
            selected = frontOut,
            warningHex = "#FFB800",
            code = "EXTERIOR WINDSHIELD",
            title = "앞유리 바깥쪽 · 물기 결로",
            subNormal = "와이퍼 1회 작동 · 유리 냉기 차단",
            subActive = "✓ 와이퍼 작동 및 유리 냉기 차단 중"
        )

        drawSciFiNode(
            canvas = canvas,
            card = nodeFrontIn,
            elbowX = txFrontIn - dp(18f),
            elbowY = nodeFrontIn.bottom + dp(14f),
            targetX = txFrontIn,
            targetY = tyFrontIn,
            fromBottom = true,
            selected = frontIn,
            warningHex = "#38BDF8",
            code = "INTERIOR WINDSHIELD",
            title = "앞유리 안쪽 · 실내 김서림",
            subNormal = "전면 제습 송풍 · 외기 유입 전환",
            subActive = "✓ 전면 제습(FRONT) · 외기 유입 가동 중"
        )

        drawSciFiNode(
            canvas = canvas,
            card = nodeMirror,
            elbowX = txMirror - dp(42f),
            elbowY = nodeMirror.top - dp(14f),
            targetX = txMirror,
            targetY = tyMirror,
            fromBottom = false,
            selected = sideMirror,
            warningHex = "#00F0FF",
            code = "SIDE MIRROR OPTICS",
            title = "사이드미러 · 김서림 감지",
            subNormal = "좌·우 사이드미러 열선 즉시 가동",
            subActive = "✓ 좌·우 사이드미러 열선 가동 중"
        )

        drawSciFiNode(
            canvas = canvas,
            card = nodeRear,
            elbowX = txRear + dp(28f),
            elbowY = nodeRear.bottom + dp(14f),
            targetX = txRear,
            targetY = tyRear,
            fromBottom = true,
            selected = rearGlass,
            warningHex = "#FFB800",
            code = "REAR GLASS THERMAL",
            title = "뒷유리 · 김서림·서리 감지",
            subNormal = "후면 유리 열선(REAR) 자동 가동",
            subActive = "✓ 뒷유리 열선(REAR) 가동 중"
        )

        // Bottom-right subtle HUD telemetry watermark
        subTextPaint.textSize = sp(8.5f)
        subTextPaint.color = Color.argb(120, 148, 163, 184)
        subTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "CLEARVIEW // PLEOS OPTICAL CARE HUD",
            stage.right - dp(18f),
            stage.bottom - dp(16f),
            subTextPaint
        )

        canvas.restore()

        // Outer 1px cyan specular stage border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(55, 56, 189, 248)
        canvas.drawRoundRect(stage, dp(20f), dp(20f), paint)
    }

    private fun drawGlowingGlassLine(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int
    ) {
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeCap = Paint.Cap.ROUND
        glowPaint.strokeWidth = dp(6f)
        glowPaint.color = Color.argb(130, Color.red(color), Color.green(color), Color.blue(color))
        glowPaint.maskFilter = BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawLine(x1, y1, x2, y2, glowPaint)
        glowPaint.maskFilter = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(2.2f)
        paint.color = color
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private fun drawSciFiNode(
        canvas: Canvas,
        card: RectF,
        elbowX: Float,
        elbowY: Float,
        targetX: Float,
        targetY: Float,
        fromBottom: Boolean,
        selected: Boolean,
        warningHex: String,
        code: String,
        title: String,
        subNormal: String,
        subActive: String
    ) {
        val accent = when {
            !selected -> Color.parseColor("#475569")
            isApplying -> Color.parseColor("#00F0FF")
            else -> Color.parseColor(warningHex)
        }

        val pulse = 0.5f + 0.5f * sin(phase * Math.PI * 4).toFloat()

        // 1. 45-degree angled 1px leader line
        val anchorX = card.centerX()
        val anchorY = if (fromBottom) card.bottom else card.top

        path.reset()
        path.moveTo(anchorX, anchorY)
        path.lineTo(elbowX, elbowY)
        path.lineTo(targetX, targetY)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.3f)
        paint.color = Color.argb(if (selected) 200 else 80, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, paint)

        // Small joint node at elbow
        paint.style = Paint.Style.FILL
        canvas.drawCircle(elbowX, elbowY, dp(2.2f), paint)

        // 2. Dual-Ring Target Reticle (◎) on the exact glass/mirror coordinate
        if (selected) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((35 + 45 * pulse).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(targetX, targetY, dp(13f) + dp(4f) * pulse, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.4f)
            paint.color = accent
            paint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(3.5f)), phase * 60f)
            canvas.drawCircle(targetX, targetY, dp(9.5f), paint)
            paint.pathEffect = null
        }

        paint.style = Paint.Style.FILL
        paint.color = accent
        canvas.drawCircle(targetX, targetY, dp(4f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(targetX, targetY, dp(1.8f), paint)

        // 3. Smoked-Glass HUD Node Card
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            card.left, card.top, card.right, card.bottom,
            Color.argb(215, 11, 20, 38),
            Color.argb(185, 7, 13, 25),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(card, dp(8f), dp(8f), paint)
        paint.shader = null

        // Subtle 1px card border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(if (selected) 95 else 40, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(card, dp(8f), dp(8f), paint)

        // Glowing left vertical accent bar (3dp)
        rect.set(card.left, card.top + dp(6f), card.left + dp(3f), card.bottom - dp(6f))
        paint.style = Paint.Style.FILL
        paint.color = accent
        canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), paint)

        // Micro telemetry code row
        subTextPaint.textAlign = Paint.Align.LEFT
        subTextPaint.textSize = sp(8f)
        subTextPaint.color = accent
        canvas.drawText(code, card.left + dp(12f), card.top + dp(14f), subTextPaint)

        // Glowing dot on top-right of card
        canvas.drawCircle(card.right - dp(12f), card.top + dp(11f), dp(3f), paint)

        // Title
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(12f)
        textPaint.color = if (selected) Color.parseColor("#F8FAFC") else Color.parseColor("#94A3B8")
        canvas.drawText(title, card.left + dp(12f), card.top + dp(31f), textPaint)

        // Subtitle
        subTextPaint.textSize = sp(10f)
        subTextPaint.color = when {
            !selected -> Color.parseColor("#64748B")
            isApplying -> Color.parseColor("#67E8F9")
            else -> Color.parseColor("#CBD5E1")
        }
        val subLine = when {
            !selected -> "시야 깨끗함 (터치하여 전환)"
            isApplying -> subActive
            else -> subNormal
        }
        canvas.drawText(subLine, card.left + dp(12f), card.top + dp(47f), subTextPaint)
    }

    private fun drawBottomConsoleBar(canvas: Canvas, bar: RectF) {
        // Smoked-glass bar background
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            bar.left, bar.top, bar.right, bar.bottom,
            Color.argb(235, 10, 18, 34),
            Color.argb(225, 13, 24, 45),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bar, dp(15f), dp(15f), paint)
        paint.shader = null

        // 1px cyan specular border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(75, 56, 189, 248)
        canvas.drawRoundRect(bar, dp(15f), dp(15f), paint)

        val zones = mutableListOf<String>()
        val actions = mutableListOf<String>()
        if (frontIn) { zones.add("앞유리(안쪽)"); actions.add("전면 제습") }
        if (frontOut) { zones.add("앞유리(바깥쪽)"); actions.add("와이퍼 1회") }
        if (sideMirror) { zones.add("사이드미러"); actions.add("미러 열선") }
        if (rearGlass) { zones.add("뒷유리"); actions.add("뒷유리 열선") }

        val beaconColor: Int
        val alertTitle: String
        val alertSub: String
        val btnLabel: String

        if (zones.isEmpty()) {
            beaconColor = Color.parseColor("#00F0FF")
            alertTitle = "모든 유리와 사이드미러 시야가 깨끗합니다"
            alertSub = "기존 오토(AUTO) 공조 상태로 유지 중입니다"
            btnLabel = "전체 재점검"
        } else if (isApplying) {
            beaconColor = Color.parseColor("#00F0FF")
            alertTitle = "${actions.joinToString(" · ")}을 동시에 가동 중입니다"
            alertSub = "김서림과 물기가 제거되면 기존 공조 설정으로 자동 복귀합니다"
            btnLabel = "작동 완료"
        } else {
            beaconColor = Color.parseColor("#FFB800")
            alertTitle = "${zones.joinToString(" · ")}에 김서림·물기가 감지되었습니다"
            alertSub = "${actions.joinToString(" + ")}을 동시에 자동 작동합니다"
            btnLabel = "시야 복구 적용"
        }

        // Pulsing status beacon on left
        val bx = bar.left + dp(24f)
        val by = bar.centerY()
        val pulse = 0.5f + 0.5f * sin(phase * Math.PI * 4).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((45 + 60 * pulse).toInt(), Color.red(beaconColor), Color.green(beaconColor), Color.blue(beaconColor))
        canvas.drawCircle(bx, by, dp(10f) + dp(3f) * pulse, paint)

        paint.color = beaconColor
        canvas.drawCircle(bx, by, dp(5f), paint)

        // Alert Title & Subtitle
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(13.5f)
        textPaint.color = Color.parseColor("#F8FAFC")
        canvas.drawText(alertTitle, bar.left + dp(44f), bar.top + dp(26f), textPaint)

        subTextPaint.textAlign = Paint.Align.LEFT
        subTextPaint.textSize = sp(11f)
        subTextPaint.color = Color.parseColor("#94A3B8")
        canvas.drawText(alertSub, bar.left + dp(44f), bar.top + dp(45f), subTextPaint)

        // Futuristic Apply Button on right
        val btnW = dp(136f)
        val btnH = dp(40f)
        applyBtnRect.set(bar.right - dp(14f) - btnW, bar.centerY() - btnH * 0.5f, bar.right - dp(14f), bar.centerY() + btnH * 0.5f)

        paint.style = Paint.Style.FILL
        paint.shader = if (isApplying) {
            LinearGradient(
                applyBtnRect.left, applyBtnRect.top, applyBtnRect.right, applyBtnRect.bottom,
                Color.parseColor("#059669"),
                Color.parseColor("#0D9488"),
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                applyBtnRect.left, applyBtnRect.top, applyBtnRect.right, applyBtnRect.bottom,
                Color.parseColor("#0284C7"),
                Color.parseColor("#2563EB"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(applyBtnRect, dp(10f), dp(10f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = if (isApplying) Color.parseColor("#34D399") else Color.parseColor("#38BDF8")
        canvas.drawRoundRect(applyBtnRect, dp(10f), dp(10f), paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(btnLabel, applyBtnRect.centerX(), applyBtnRect.centerY() + dp(4.5f), textPaint)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}

