package ai.pleos.playground.clearview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * Single-screen, tab-free All-Around Vehicle Visibility Status Canvas.
 * Displays Front Windshield (Inside & Outside), Left/Right Camera Side Mirrors (DSM),
 * and Rear Windshield ALL AT ONCE on a single intuitive screen.
 */
class StatusCanvasView(
    context: Context,
    private val onZoneToggled: () -> Unit
) : View(context) {

    var frontInsideFog: Boolean = true
    var frontOutsideWater: Boolean = true
    var dsmMirrorFog: Boolean = true
    var rearGlassFog: Boolean = true

    var isApplyingAction: Boolean = false
        private set

    private var animPhase: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    private val leftZoneRect = RectF()
    private val centerZoneRect = RectF()
    private val rightTopZoneRect = RectF()
    private val rightBotZoneRect = RectF()

    fun setApplying(applying: Boolean) {
        isApplyingAction = applying
        if (!animator.isStarted) animator.start()
        invalidate()
    }

    fun resetAllZonesActive() {
        frontInsideFog = true
        frontOutsideWater = true
        dsmMirrorFog = true
        rearGlassFog = true
        isApplyingAction = false
        invalidate()
    }

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
                leftZoneRect.contains(x, y) -> {
                    if (y < leftZoneRect.centerY()) {
                        frontInsideFog = !frontInsideFog
                    } else {
                        frontOutsideWater = !frontOutsideWater
                    }
                    onZoneToggled()
                    invalidate()
                }
                rightTopZoneRect.contains(x, y) -> {
                    dsmMirrorFog = !dsmMirrorFog
                    onZoneToggled()
                    invalidate()
                }
                rightBotZoneRect.contains(x, y) -> {
                    rearGlassFog = !rearGlassFog
                    onZoneToggled()
                    invalidate()
                }
                centerZoneRect.contains(x, y) -> {
                    // Tapping center car resets all zones active
                    resetAllZonesActive()
                    onZoneToggled()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Studio dark-slate background
        rect.set(0f, 0f, w, h)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#0F172A"),
            Color.parseColor("#1E293B"),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, dp(22f), dp(22f), paint)
        paint.shader = null

        val pad = dp(14f)
        val gap = dp(12f)
        val colW = (w - pad * 2f - gap * 2f) / 3f
        val topY = dp(14f)
        val botY = h - dp(14f)

        // 3 Columns:
        // 1. Left: Front Windshield (Inside vs Outside simultaneous cross-section)
        // 2. Center: Full Top-Down Vehicle Map (Front + Left/Right DSM Mirrors + Rear all at once)
        // 3. Right: Camera Mirror Lens Close-up (Top) + Rear Windshield Defrost (Bottom)
        leftZoneRect.set(pad, topY, pad + colW, botY)
        centerZoneRect.set(pad + colW + gap, topY, pad + colW * 2f + gap, botY)

        val rightLeft = pad + (colW + gap) * 2f
        val halfH = (botY - topY - gap) * 0.5f
        rightTopZoneRect.set(rightLeft, topY, w - pad, topY + halfH)
        rightBotZoneRect.set(rightLeft, topY + halfH + gap, w - pad, botY)

        drawFrontInsideOutsidePanel(canvas, leftZoneRect)
        drawCenterFullCarMap(canvas, centerZoneRect)
        drawRightDsmPanel(canvas, rightTopZoneRect)
        drawRightRearPanel(canvas, rightBotZoneRect)
    }

    /**
     * LEFT COLUMN:
     * Front Windshield Simultaneous View — shows BOTH Inside Fog (Defrost) & Outside Water (Wiper)
     * on one single cross-section & front diagram!
     */
    private fun drawFrontInsideOutsidePanel(canvas: Canvas, r: RectF) {
        drawCardFrame(canvas, r, frontInsideFog || frontOutsideWater, "#3B82F6")

        // Header badge
        drawHeaderPill(canvas, r.left + dp(12f), r.top + dp(10f), "앞유리 (안쪽 제습 · 바깥쪽 와이퍼)", "#2563EB")

        val cx = r.centerX()
        // Slanted windshield cross-section in the middle showing BOTH sides of the glass at once!
        val gxBot = r.left + r.width() * 0.34f
        val gyBot = r.bottom - dp(52f)
        val gxTop = r.left + r.width() * 0.62f
        val gyTop = r.top + dp(56f)

        // Car Hood (left) & Car Roof (right)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(5f)
        paint.color = Color.parseColor("#64748B")
        canvas.drawLine(r.left + dp(18f), gyBot, gxBot, gyBot, paint)
        canvas.drawLine(gxTop, gyTop, r.right - dp(18f), gyTop, paint)

        // Slanted Windshield Glass Bar
        paint.strokeWidth = dp(8f)
        paint.color = Color.parseColor("#60A5FA")
        canvas.drawLine(gxBot, gyBot, gxTop, gyTop, paint)

        // 1) OUTER SIDE (Left of glass): Amber water droplets + Wiper action
        textPaint.textSize = sp(11f)
        textPaint.color = if (frontOutsideWater) Color.parseColor("#FBBF24") else Color.parseColor("#64748B")
        canvas.drawText("바깥쪽(실외)", r.left + r.width() * 0.23f, r.top + dp(74f), textPaint)
        textPaint.textSize = sp(10f)
        canvas.drawText("와이퍼 제거", r.left + r.width() * 0.23f, r.top + dp(90f), textPaint)

        if (frontOutsideWater) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F59E0B")
            for (i in 1..5) {
                val t = i / 6f
                val px = gxBot + (gxTop - gxBot) * t - dp(11f)
                val py = gyBot + (gyTop - gyBot) * t - dp(4f)
                canvas.drawCircle(px, py, dp(5f), paint)
            }
            // Wiper sweep indicator on outer glass
            val shift = if (isApplyingAction) sin(animPhase * Math.PI * 2).toFloat() * dp(12f) else 0f
            drawVectorArrow(
                canvas,
                gxBot - dp(16f),
                gyBot - dp(24f) + shift,
                gxTop - dp(20f),
                gyTop + dp(24f) + shift,
                Color.parseColor("#FBBF24"),
                dp(3f)
            )
        }

        // 2) INNER SIDE (Right of glass): Blue cabin mist + Upward Defrost Airflow
        textPaint.textSize = sp(11f)
        textPaint.color = if (frontInsideFog) Color.parseColor("#93C5FD") else Color.parseColor("#64748B")
        canvas.drawText("안쪽(실내)", r.left + r.width() * 0.78f, r.top + dp(96f), textPaint)
        textPaint.textSize = sp(10f)
        canvas.drawText("전면 제습(FRONT)", r.left + r.width() * 0.78f, r.top + dp(112f), textPaint)

        if (frontInsideFog) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(215, 191, 219, 254)
            for (i in 1..5) {
                val t = i / 6f
                val px = gxBot + (gxTop - gxBot) * t + dp(11f)
                val py = gyBot + (gyTop - gyBot) * t + dp(4f)
                canvas.drawCircle(px, py, dp(5.5f), paint)
            }
            val upShift = if (isApplyingAction) animPhase * dp(16f) else 0f
            drawVectorArrow(
                canvas,
                gxBot + dp(24f),
                gyBot - dp(8f) - upShift,
                gxTop + dp(16f),
                gyTop + dp(34f) - upShift,
                Color.parseColor("#60A5FA"),
                dp(3.5f)
            )
        }

        // Bottom status summary tag
        val footerText = when {
            isApplyingAction -> "✓ 제습 송풍 + 와이퍼 동시 작동 중"
            frontInsideFog && frontOutsideWater -> "안쪽(제습) · 바깥쪽(와이퍼) 동시 케어"
            frontInsideFog -> "안쪽 김서림 → 전면 제습(FRONT)"
            frontOutsideWater -> "바깥쪽 물기 → 와이퍼 1회(MIST)"
            else -> "✓ 앞유리 깨끗함"
        }
        drawFooterBar(canvas, r, footerText, "#1D4ED8")
    }

    /**
     * CENTER COLUMN:
     * Full Top-Down Vehicle Silhouette showing:
     * - Front Windshield (Top)
     * - Left & Right Camera Side Mirrors (DSM) (Sides)
     * - Rear Windshield (Bottom)
     * ALL ON ONE UNIFIED CAR DIAGRAM!
     */
    private fun drawCenterFullCarMap(canvas: Canvas, r: RectF) {
        drawCardFrame(canvas, r, true, "#10B981")
        drawHeaderPill(canvas, r.left + dp(12f), r.top + dp(10f), "차량 전체 상태 (앞 · 미러 · 뒤)", "#059669")

        val cx = r.centerX()
        val carTop = r.top + dp(48f)
        val carBot = r.bottom - dp(46f)
        val carH = carBot - carTop
        val carHalfW = r.width() * 0.25f

        // 1. Car Body Silhouette (Top-Down)
        rect.set(cx - carHalfW, carTop, cx + carHalfW, carBot)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(rect, dp(28f), dp(28f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#475569")
        canvas.drawRoundRect(rect, dp(28f), dp(28f), paint)

        // 2. FRONT WINDSHIELD on the top-down car
        val fwTop = carTop + carH * 0.16f
        val fwBot = carTop + carH * 0.34f
        path.reset()
        path.moveTo(cx - carHalfW * 0.82f, fwTop)
        path.lineTo(cx + carHalfW * 0.82f, fwTop)
        path.lineTo(cx + carHalfW * 0.72f, fwBot)
        path.lineTo(cx - carHalfW * 0.72f, fwBot)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = if (frontInsideFog || frontOutsideWater) {
            Color.parseColor("#1D4ED8")
        } else {
            Color.parseColor("#065F46")
        }
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = if (frontInsideFog || frontOutsideWater) {
            Color.parseColor("#60A5FA")
        } else {
            Color.parseColor("#34D399")
        }
        canvas.drawPath(path, paint)

        textPaint.textSize = sp(10f)
        textPaint.color = Color.WHITE
        canvas.drawText("앞유리 (FRONT)", cx, (fwTop + fwBot) * 0.5f + dp(4f), textPaint)

        // 3. LEFT & RIGHT CAMERA SIDE MIRRORS (DSM) sticking out from the car sides!
        val mirrorY = fwBot - dp(4f)
        val wingW = dp(34f)
        val wingH = dp(18f)

        // Left DSM Wing
        rect.set(cx - carHalfW - wingW, mirrorY - wingH * 0.5f, cx - carHalfW + dp(2f), mirrorY + wingH * 0.5f)
        drawMiniDsmWing(canvas, rect, isLeft = true)

        // Right DSM Wing
        rect.set(cx + carHalfW - dp(2f), mirrorY - wingH * 0.5f, cx + carHalfW + wingW, mirrorY + wingH * 0.5f)
        drawMiniDsmWing(canvas, rect, isLeft = false)

        // Cabin Roof Label
        textPaint.textSize = sp(10f)
        textPaint.color = Color.parseColor("#94A3B8")
        canvas.drawText("실내 루프", cx, (fwBot + carTop + carH * 0.68f) * 0.5f + dp(4f), textPaint)

        // 4. REAR WINDSHIELD on the bottom of the top-down car
        val rwTop = carTop + carH * 0.68f
        val rwBot = carTop + carH * 0.85f
        path.reset()
        path.moveTo(cx - carHalfW * 0.72f, rwTop)
        path.lineTo(cx + carHalfW * 0.72f, rwTop)
        path.lineTo(cx + carHalfW * 0.80f, rwBot)
        path.lineTo(cx - carHalfW * 0.80f, rwBot)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = if (rearGlassFog) Color.parseColor("#7C2D12") else Color.parseColor("#065F46")
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = if (rearGlassFog) Color.parseColor("#FB923C") else Color.parseColor("#34D399")
        canvas.drawPath(path, paint)

        // Horizontal rear defroster heating lines inside rear windshield
        if (rearGlassFog) {
            paint.strokeWidth = dp(1.2f)
            paint.color = Color.argb(170, 251, 146, 60)
            canvas.drawLine(cx - carHalfW * 0.65f, rwTop + dp(6f), cx + carHalfW * 0.65f, rwTop + dp(6f), paint)
            canvas.drawLine(cx - carHalfW * 0.68f, rwBot - dp(6f), cx + carHalfW * 0.68f, rwBot - dp(6f), paint)
        }

        textPaint.textSize = sp(10f)
        textPaint.color = Color.WHITE
        canvas.drawText("뒷유리 (REAR)", cx, (rwTop + rwBot) * 0.5f + dp(4f), textPaint)

        drawFooterBar(
            canvas,
            r,
            if (isApplyingAction) "✓ 앞 · 좌/우 미러 · 뒤 전체 동시 작동 중" else "앞유리 · 좌/우 미러 · 뒷유리 한눈에 보기",
            "#047857"
        )
    }

    private fun drawMiniDsmWing(canvas: Canvas, wingRect: RectF, isLeft: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (dsmMirrorFog) Color.parseColor("#065F46") else Color.parseColor("#334155")
        canvas.drawRoundRect(wingRect, dp(6f), dp(6f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.8f)
        paint.color = if (dsmMirrorFog) Color.parseColor("#34D399") else Color.parseColor("#64748B")
        canvas.drawRoundRect(wingRect, dp(6f), dp(6f), paint)

        // Pulsing dot at the outer-bottom corner of the DSM wing
        val lensX = if (isLeft) wingRect.left + dp(6f) else wingRect.right - dp(6f)
        val lensY = wingRect.bottom - dp(4f)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawCircle(lensX, lensY, dp(3.5f), paint)

        textPaint.textSize = sp(9f)
        textPaint.color = Color.parseColor("#6EE7B7")
        canvas.drawText(
            if (isLeft) "좌 미러" else "우 미러",
            wingRect.centerX(),
            wingRect.top - dp(4f),
            textPaint
        )
    }

    /**
     * RIGHT TOP PANEL:
     * Camera Side Mirror (DSM) Close-up — shows the exact outer-bottom lens spot + heater waves!
     */
    private fun drawRightDsmPanel(canvas: Canvas, r: RectF) {
        drawCardFrame(canvas, r, dsmMirrorFog, "#10B981")
        drawHeaderPill(canvas, r.left + dp(10f), r.top + dp(8f), "카메라 미러(DSM) 렌즈 위치·열선", "#059669")

        // DSM Arm extending from right door body toward left
        val armLeft = r.left + dp(18f)
        val armRight = r.right - dp(18f)
        val armTop = r.top + dp(38f)
        val armBot = armTop + dp(36f)
        rect.set(armLeft, armTop, armRight, armBot)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.parseColor("#34D399")
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)

        textPaint.textSize = sp(10f)
        textPaint.color = Color.parseColor("#E2E8F0")
        canvas.drawText("미러 암 하단 끝 = 카메라 렌즈 위치", rect.centerX() + dp(12f), rect.centerY() + dp(4f), textPaint)

        // Pulsing Camera Lens on outer-bottom tip
        val lensX = armLeft + dp(20f)
        val lensY = armBot - dp(3f)
        val pulse = 0.5f + 0.5f * sin(animPhase * Math.PI * 2).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((60 + 90 * pulse).toInt(), 245, 158, 11)
        canvas.drawCircle(lensX, lensY, dp(12f), paint)
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(lensX, lensY, dp(5.5f), paint)

        textPaint.textSize = sp(10f)
        textPaint.color = Color.parseColor("#FDE68A")
        canvas.drawText(
            if (isApplyingAction) "♨ 좌·우 미러 렌즈 열선 건조 중" else "★ 렌즈 위치: 미러 바깥쪽 끝 하단부",
            r.centerX(),
            r.bottom - dp(12f),
            textPaint
        )
    }

    /**
     * RIGHT BOTTOM PANEL:
     * Rear Windshield (뒷유리 REAR 열선) — shows horizontal heating grid clearing rear fog/frost!
     */
    private fun drawRightRearPanel(canvas: Canvas, r: RectF) {
        drawCardFrame(canvas, r, rearGlassFog, "#F97316")
        drawHeaderPill(canvas, r.left + dp(10f), r.top + dp(8f), "뒷유리 (REAR 열선 제습)", "#EA580C")

        val gLeft = r.left + dp(24f)
        val gRight = r.right - dp(24f)
        val gTop = r.top + dp(38f)
        val gBot = r.bottom - dp(28f)
        rect.set(gLeft, gTop, gRight, gBot)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = if (rearGlassFog) Color.parseColor("#FB923C") else Color.parseColor("#64748B")
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)

        // Rear Defroster horizontal orange heating lines
        paint.strokeWidth = dp(2f)
        val glowAlpha = if (isApplyingAction) {
            (160 + 95 * sin(animPhase * Math.PI * 2).toFloat()).toInt()
        } else {
            210
        }
        paint.color = Color.argb(glowAlpha, 251, 146, 60)
        for (i in 1..3) {
            val ly = gTop + (gBot - gTop) * (i / 4f)
            canvas.drawLine(gLeft + dp(10f), ly, gRight - dp(10f), ly, paint)
        }

        textPaint.textSize = sp(10f)
        textPaint.color = Color.parseColor("#FFEDD5")
        canvas.drawText(
            if (isApplyingAction) "♨ 뒷유리 열선(REAR) 가동 중" else "뒷유리 습기·서리 → REAR 열선 가동",
            r.centerX(),
            r.bottom - dp(10f),
            textPaint
        )
    }

    private fun drawCardFrame(canvas: Canvas, r: RectF, active: Boolean, activeStrokeHex: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#172336")
        canvas.drawRoundRect(r, dp(16f), dp(16f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (active) dp(2f) else dp(1f)
        paint.color = if (active) Color.parseColor(activeStrokeHex) else Color.parseColor("#334155")
        canvas.drawRoundRect(r, dp(16f), dp(16f), paint)
    }

    private fun drawHeaderPill(canvas: Canvas, x: Float, y: Float, text: String, bgHex: String) {
        textPaint.textSize = sp(10.5f)
        val bw = textPaint.measureText(text) + dp(18f)
        rect.set(x, y, x + bw, y + dp(22f))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(bgHex)
        canvas.drawRoundRect(rect, dp(11f), dp(11f), paint)

        textPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + dp(3.5f), textPaint)
    }

    private fun drawFooterBar(canvas: Canvas, r: RectF, text: String, bgHex: String) {
        rect.set(r.left + dp(10f), r.bottom - dp(32f), r.right - dp(10f), r.bottom - dp(8f))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(bgHex)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), paint)

        textPaint.textSize = sp(10f)
        textPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + dp(3.5f), textPaint)
    }

    private fun drawVectorArrow(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        strokeW: Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        canvas.drawLine(x1, y1, x2, y2, paint)

        val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLen = dp(7.5f)
        val a1 = angle - 0.55
        val a2 = angle + 0.55
        canvas.drawLine(
            x2,
            y2,
            (x2 - headLen * cos(a1)).toFloat(),
            (y2 - headLen * sin(a1)).toFloat(),
            paint
        )
        canvas.drawLine(
            x2,
            y2,
            (x2 - headLen * cos(a2)).toFloat(),
            (y2 - headLen * sin(a2)).toFloat(),
            paint
        )
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
