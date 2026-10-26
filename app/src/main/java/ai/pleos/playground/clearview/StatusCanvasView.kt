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
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ultra-intuitive 2-Panel Visual Status Screen:
 * - Left Panel: "어디에 생겼나요?" — Car windshield side cross-section (Inside vs Outside surface)
 *   or Close-up DSM Camera Mirror Arm showing exact bottom-outer lens position.
 * - Right Panel: "어떻게 없애나요?" — Live animated windshield/wiper/heater action view.
 */
class StatusCanvasView(context: Context) : View(context) {

    enum class VisualState {
        CLEAR,
        INSIDE_FOG,
        OUTSIDE_CONDENSATION,
        DSM_CAMERA_FOG
    }

    var currentState: VisualState = VisualState.INSIDE_FOG
        private set

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

    fun setState(state: VisualState, applying: Boolean) {
        currentState = state
        isApplyingAction = applying
        if (!animator.isStarted) animator.start()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Outer studio container (#0F172A -> #1E293B)
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

        // Top-Left Status Badge
        drawTopStatusBadge(canvas)

        if (currentState == VisualState.CLEAR) {
            drawAllClearView(canvas, w, h)
            return
        }

        val pad = dp(16f)
        val topY = dp(52f)
        val botY = h - dp(16f)
        val midGap = dp(14f)
        val panelW = (w - pad * 2f - midGap) * 0.5f

        val leftPanel = RectF(pad, topY, pad + panelW, botY)
        val rightPanel = RectF(pad + panelW + midGap, topY, w - pad, botY)

        // Panel backgrounds
        drawSubPanel(canvas, leftPanel)
        drawSubPanel(canvas, rightPanel)

        when (currentState) {
            VisualState.INSIDE_FOG -> {
                drawInsideCutawayLeft(canvas, leftPanel)
                drawInsideActionRight(canvas, rightPanel)
            }
            VisualState.OUTSIDE_CONDENSATION -> {
                drawOutsideCutawayLeft(canvas, leftPanel)
                drawOutsideActionRight(canvas, rightPanel)
            }
            VisualState.DSM_CAMERA_FOG -> {
                drawDsmLensDiagramLeft(canvas, leftPanel)
                drawDsmHeaterActionRight(canvas, rightPanel)
            }
            VisualState.CLEAR -> {}
        }
    }

    private fun drawSubPanel(canvas: Canvas, r: RectF) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#172336")
        canvas.drawRoundRect(r, dp(16f), dp(16f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(r, dp(16f), dp(16f), paint)
    }

    /**
     * LEFT PANEL (INSIDE FOG):
     * Side cross-section of car hood + slanted windshield + roof + dashboard vent.
     * Clearly shows fog droplets clinging to the INNER (right) side of the glass!
     */
    private fun drawInsideCutawayLeft(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#93C5FD")
        canvas.drawText("① 발생 위치: 차 안쪽(실내) 유리면", r.centerX(), r.top + dp(24f), textPaint)

        // Outdoor label (Left side of slanted glass)
        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#94A3B8")
        canvas.drawText("차 밖 (실외 찬 공기)", r.left + r.width() * 0.22f, r.top + dp(68f), textPaint)
        textPaint.textSize = sp(16f)
        textPaint.color = Color.parseColor("#38BDF8")
        canvas.drawText("2°C", r.left + r.width() * 0.22f, r.top + dp(90f), textPaint)

        // Indoor label (Right side of slanted glass, cleanly below the roof line)
        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#FDE68A")
        canvas.drawText("차 안 (실내 습기)", r.left + r.width() * 0.77f, r.top + dp(76f), textPaint)
        textPaint.textSize = sp(16f)
        textPaint.color = Color.parseColor("#FBBF24")
        canvas.drawText("22°C · 다습", r.left + r.width() * 0.77f, r.top + dp(98f), textPaint)

        // Car silhouette cross-section coordinates
        val gxBot = r.left + r.width() * 0.36f
        val gyBot = r.bottom - dp(56f)
        val gxTop = r.left + r.width() * 0.56f
        val gyTop = r.top + dp(46f)

        // Car Hood (left of bottom glass) & Car Roof (right of top glass)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(6f)
        paint.color = Color.parseColor("#64748B")
        canvas.drawLine(r.left + dp(28f), gyBot, gxBot, gyBot, paint)
        canvas.drawLine(gxTop, gyTop, r.right - dp(28f), gyTop, paint)

        // Slanted Windshield Glass Bar
        paint.strokeWidth = dp(9f)
        paint.color = Color.parseColor("#60A5FA")
        canvas.drawLine(gxBot, gyBot, gxTop, gyTop, paint)

        // Fog droplets clinging to the RIGHT (INNER) surface of the slanted windshield
        val mistAlpha = if (isApplyingAction) {
            (110 + 90 * cos(animPhase * Math.PI * 2).toFloat()).toInt()
        } else {
            235
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(mistAlpha, 191, 219, 254)
        for (i in 1..6) {
            val t = i / 7f
            val px = gxBot + (gxTop - gxBot) * t + dp(12f)
            val py = gyBot + (gyTop - gyBot) * t + dp(4f)
            canvas.drawCircle(px, py, dp(7f), paint)
        }

        // Dashboard Defrost Vent blowing warm air UP along the inner glass
        val ventX = gxBot + dp(24f)
        val ventY = gyBot + dp(6f)
        rect.set(ventX - dp(20f), ventY - dp(8f), ventX + dp(28f), ventY + dp(10f))
        paint.color = Color.parseColor("#2563EB")
        canvas.drawRoundRect(rect, dp(6f), dp(6f), paint)

        // Animated airflow arrow along inner surface
        val shift = if (isApplyingAction) animPhase * dp(18f) else 0f
        drawVectorArrow(
            canvas,
            ventX + dp(8f),
            gyBot - dp(10f) - shift,
            gxTop + dp(18f),
            gyTop + dp(38f) - shift,
            Color.parseColor("#60A5FA"),
            dp(3.5f)
        )

        drawBottomPill(canvas, r, "유리 안쪽에 맺힌 습기 → 전면 제습 바람으로 건조", "#1E40AF")
    }

    /**
     * RIGHT PANEL (INSIDE FOG):
     * Driver's front windshield view with rising defrost arrows + side mirror heat.
     */
    private fun drawInsideActionRight(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#93C5FD")
        canvas.drawText("② 자동 조치: 전면 제습(FRONT) + 외기 유입", r.centerX(), r.top + dp(24f), textPaint)

        val cx = r.centerX()
        val glassTop = r.top + dp(46f)
        val glassBot = r.bottom - dp(54f)
        val topW = r.width() * 0.30f
        val botW = r.width() * 0.40f

        // Windshield frame
        path.reset()
        path.moveTo(cx - topW, glassTop)
        path.lineTo(cx + topW, glassTop)
        path.lineTo(cx + botW, glassBot)
        path.lineTo(cx - botW, glassBot)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E3A5F")
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#60A5FA")
        canvas.drawPath(path, paint)

        // Inner mist cloud (shrinks/fades when applying)
        val alpha = if (isApplyingAction) (120 * (1f - 0.5f * sin(animPhase * Math.PI).toFloat())).toInt() else 190
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 219, 234, 254)
        canvas.drawOval(
            cx - botW * 0.75f,
            glassTop + dp(14f),
            cx + botW * 0.75f,
            glassBot - dp(24f),
            paint
        )

        // 4 Upward Defrost Arrows
        val offset = if (isApplyingAction) animPhase * dp(24f) else dp(4f)
        for (i in -1..1) {
            val ax = cx + i * dp(44f)
            drawVectorArrow(
                canvas,
                ax,
                glassBot - dp(10f) - offset,
                ax,
                glassBot - dp(48f) - offset,
                Color.parseColor("#3B82F6"),
                dp(4f)
            )
        }

        drawBottomPill(
            canvas,
            r,
            if (isApplyingAction) "✓ 전면 제습 · 에어컨 · 외기 유입 작동 중" else "전면 제습(FRONT) + 에어컨(A/C) + 외기 유입",
            "#2563EB"
        )
    }

    /**
     * LEFT PANEL (OUTSIDE CONDENSATION):
     * Side cross-section clearly showing water droplets on the OUTER (left) side of the glass!
     */
    private fun drawOutsideCutawayLeft(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#FDE68A")
        canvas.drawText("① 발생 위치: 차 바깥쪽(실외) 유리 표면", r.centerX(), r.top + dp(24f), textPaint)

        // Outdoor label (Left side)
        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#FBBF24")
        canvas.drawText("차 밖 (여름 고온다습)", r.left + r.width() * 0.22f, r.top + dp(68f), textPaint)
        textPaint.textSize = sp(16f)
        textPaint.color = Color.parseColor("#F59E0B")
        canvas.drawText("30°C · 습함", r.left + r.width() * 0.22f, r.top + dp(90f), textPaint)

        // Indoor label (Right side, cleanly below the roof line)
        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#93C5FD")
        canvas.drawText("차 안 (에어컨 냉기)", r.left + r.width() * 0.77f, r.top + dp(76f), textPaint)
        textPaint.textSize = sp(16f)
        textPaint.color = Color.parseColor("#38BDF8")
        canvas.drawText("19°C 찬바람", r.left + r.width() * 0.77f, r.top + dp(98f), textPaint)

        val gxBot = r.left + r.width() * 0.38f
        val gyBot = r.bottom - dp(56f)
        val gxTop = r.left + r.width() * 0.58f
        val gyTop = r.top + dp(46f)

        // Hood & Roof
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(6f)
        paint.color = Color.parseColor("#64748B")
        canvas.drawLine(r.left + dp(28f), gyBot, gxBot, gyBot, paint)
        canvas.drawLine(gxTop, gyTop, r.right - dp(28f), gyTop, paint)

        // Slanted Windshield
        paint.strokeWidth = dp(9f)
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawLine(gxBot, gyBot, gxTop, gyTop, paint)

        // Water droplets clinging to the LEFT (OUTER) surface of the slanted windshield!
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F59E0B")
        for (i in 1..6) {
            val t = i / 7f
            val px = gxBot + (gxTop - gxBot) * t - dp(12f)
            val py = gyBot + (gyTop - gyBot) * t - dp(4f)
            canvas.drawCircle(px, py, dp(6.5f), paint)
        }

        // Redirect indoor airflow horizontally AWAY from windshield
        drawVectorArrow(
            canvas,
            gxBot + dp(24f),
            gyBot - dp(12f),
            r.right - dp(36f),
            gyBot - dp(28f),
            Color.parseColor("#FBBF24"),
            dp(3.5f)
        )

        drawBottomPill(canvas, r, "유리 바깥쪽 물기 → 에어컨이 아니라 와이퍼로 제거!", "#B45309")
    }

    /**
     * RIGHT PANEL (OUTSIDE CONDENSATION):
     * Front windshield with sweeping wiper blade wiping outer water droplets off.
     */
    private fun drawOutsideActionRight(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#FDE68A")
        canvas.drawText("② 자동 조치: 와이퍼 1회 작동 + 유리 찬바람 차단", r.centerX(), r.top + dp(24f), textPaint)

        val cx = r.centerX()
        val glassTop = r.top + dp(46f)
        val glassBot = r.bottom - dp(54f)
        val topW = r.width() * 0.30f
        val botW = r.width() * 0.40f

        path.reset()
        path.moveTo(cx - topW, glassTop)
        path.lineTo(cx + topW, glassTop)
        path.lineTo(cx + botW, glassBot)
        path.lineTo(cx - botW, glassBot)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E3A5F")
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawPath(path, paint)

        // Wiper Sweep Sector
        val pivotX = cx
        val pivotY = glassBot - dp(4f)
        val wipeRadius = (glassBot - glassTop) * 0.86f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
        paint.color = Color.parseColor("#F59E0B")
        rect.set(pivotX - wipeRadius, pivotY - wipeRadius, pivotX + wipeRadius, pivotY + wipeRadius)
        canvas.drawArc(rect, 215f, 110f, false, paint)
        paint.pathEffect = null

        // Animated Wiper Arm
        val angleDeg = if (isApplyingAction) {
            270f + 46f * sin(animPhase * Math.PI * 2).toFloat()
        } else {
            238f
        }
        val rad = Math.toRadians(angleDeg.toDouble())
        val tipX = pivotX + (wipeRadius * cos(rad)).toFloat()
        val tipY = pivotY + (wipeRadius * sin(rad)).toFloat()

        paint.strokeWidth = dp(4.5f)
        paint.color = Color.WHITE
        canvas.drawLine(pivotX, pivotY, tipX, tipY, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(pivotX, pivotY, dp(5.5f), paint)

        drawBottomPill(
            canvas,
            r,
            if (isApplyingAction) "✓ 와이퍼 1회(MIST) 작동 · 유리 송풍 차단 완료" else "와이퍼 1회(MIST) + 유리 찬바람 차단 + 온도 상향",
            "#D97706"
        )
    }

    /**
     * LEFT PANEL (DSM CAMERA MIRROR):
     * Close-up illustration of the Camera Side Mirror (DSM) arm attached to the car door,
     * pinpointing the exact bottom-outer camera lens location!
     */
    private fun drawDsmLensDiagramLeft(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#6EE7B7")
        canvas.drawText("① 렌즈 위치: 미러 암 바깥쪽 끝단 아래!", r.centerX(), r.top + dp(24f), textPaint)

        // Car Door Body on the right side of the diagram
        val doorLeft = r.right - dp(68f)
        val doorRight = r.right - dp(20f)
        rect.set(doorLeft, r.top + dp(46f), doorRight, r.bottom - dp(54f))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)

        textPaint.textSize = sp(10f)
        textPaint.color = Color.parseColor("#CBD5E1")
        canvas.drawText("차체 도어", rect.centerX(), rect.centerY(), textPaint)

        // DSM Aerodynamic Camera Arm extending to the left
        val armLeft = r.left + dp(34f)
        val armRight = doorLeft + dp(8f)
        val armTop = r.top + dp(66f)
        val armBot = armTop + dp(56f)
        rect.set(armLeft, armTop, armRight, armBot)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#34D399")
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)

        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#E2E8F0")
        canvas.drawText("카메라 사이드미러(DSM) 외장 암", rect.centerX(), rect.centerY() - dp(2f), textPaint)

        // Exact Camera Lens at the OUTER-BOTTOM corner (armLeft + 26dp, armBot)
        val lensX = armLeft + dp(28f)
        val lensY = armBot - dp(4f)

        val pulse = 0.5f + 0.5f * sin(animPhase * Math.PI * 2).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((60 + 90 * pulse).toInt(), 245, 158, 11)
        canvas.drawCircle(lensX, lensY, dp(18f), paint)

        paint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(lensX, lensY, dp(8f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(lensX, lensY, dp(3.5f), paint)

        // Pointer arrow from bottom up to the lens
        drawVectorArrow(
            canvas,
            lensX,
            armBot + dp(38f),
            lensX,
            lensY + dp(12f),
            Color.parseColor("#FBBF24"),
            dp(3.5f)
        )

        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#FDE68A")
        canvas.drawText("★ 실제 카메라 렌즈 (바깥쪽 끝 하단)", r.centerX() - dp(12f), armBot + dp(52f), textPaint)

        drawBottomPill(canvas, r, "거울이 없어 손으로 못 찾던 렌즈 위치 확인!", "#065F46")
    }

    /**
     * RIGHT PANEL (DSM CAMERA MIRROR):
     * Left & Right DSM mirror heater coils warming the camera lens + clear digital monitor view.
     */
    private fun drawDsmHeaterActionRight(canvas: Canvas, r: RectF) {
        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#6EE7B7")
        canvas.drawText("② 자동 조치: 좌·우 카메라 미러 열선(♨) 가동", r.centerX(), r.top + dp(24f), textPaint)

        // Digital Side Mirror (DSM) Cabin Monitor View
        val monLeft = r.left + dp(28f)
        val monRight = r.right - dp(28f)
        val monTop = r.top + dp(48f)
        val monBot = r.bottom - dp(56f)
        rect.set(monLeft, monTop, monRight, monBot)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F243C")
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#10B981")
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)

        // Road perspective lines inside the DSM monitor
        val cx = rect.centerX()
        paint.strokeWidth = dp(2.5f)
        paint.color = Color.parseColor("#64748B")
        canvas.drawLine(cx - dp(54f), monBot - dp(12f), cx - dp(16f), monTop + dp(36f), paint)
        canvas.drawLine(cx + dp(54f), monBot - dp(12f), cx + dp(16f), monTop + dp(36f), paint)

        // Animated Red/Orange Heating Waves (♨)
        val waveShift = if (isApplyingAction) sin(animPhase * Math.PI * 2).toFloat() * dp(4f) else 0f
        paint.color = Color.parseColor("#FB923C")
        paint.strokeWidth = dp(3f)
        for (i in -1..1) {
            val wx = cx + i * dp(28f)
            val wy = rect.centerY() + waveShift
            canvas.drawLine(wx, wy + dp(12f), wx, wy - dp(12f), paint)
        }

        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#A7F3D0")
        canvas.drawText(
            if (isApplyingAction) "♨ 미러 하우징 열선으로 렌즈 습기 건조 중" else "주행 중 창문 밖으로 손을 뻗지 마세요",
            cx,
            monTop + dp(26f),
            textPaint
        )

        drawBottomPill(
            canvas,
            r,
            if (isApplyingAction) "✓ 좌·우 카메라 미러 열선 + 전면 제습 가동 중" else "좌·우 미러 열선 ON + 정차(P) 시 렌즈 위치 표시",
            "#059669"
        )
    }

    private fun drawAllClearView(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val cy = h * 0.54f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(45, 16, 185, 129)
        canvas.drawCircle(cx, cy - dp(12f), dp(42f), paint)

        textPaint.textSize = sp(28f)
        textPaint.color = Color.parseColor("#34D399")
        canvas.drawText("✓", cx, cy - dp(2f), textPaint)

        textPaint.textSize = sp(17f)
        textPaint.color = Color.WHITE
        canvas.drawText("앞유리 및 좌·우 카메라 미러 시야 확보 완료", cx, cy + dp(46f), textPaint)
    }

    private fun drawBottomPill(canvas: Canvas, r: RectF, text: String, hexColor: String) {
        rect.set(r.left + dp(14f), r.bottom - dp(38f), r.right - dp(14f), r.bottom - dp(10f))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(hexColor)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)

        textPaint.textSize = sp(11f)
        textPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + dp(4f), textPaint)
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
        val headLen = dp(9f)
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

    private fun drawTopStatusBadge(canvas: Canvas) {
        val (badgeLabel, badgeColor) = when (currentState) {
            VisualState.CLEAR -> "상태 · 모든 시야 깨끗함" to Color.parseColor("#10B981")
            VisualState.INSIDE_FOG -> "상태 · 유리 안쪽 김서림" to Color.parseColor("#2563EB")
            VisualState.OUTSIDE_CONDENSATION -> "상태 · 유리 바깥쪽 물기" to Color.parseColor("#D97706")
            VisualState.DSM_CAMERA_FOG -> "상태 · 카메라 미러 김서림" to Color.parseColor("#059669")
        }

        textPaint.textSize = sp(12f)
        val bw = textPaint.measureText(badgeLabel) + dp(26f)
        rect.set(dp(16f), dp(12f), dp(16f) + bw, dp(38f))
        paint.style = Paint.Style.FILL
        paint.color = badgeColor
        canvas.drawRoundRect(rect, dp(13f), dp(13f), paint)

        textPaint.color = Color.WHITE
        canvas.drawText(badgeLabel, rect.centerX(), rect.centerY() + dp(4f), textPaint)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
