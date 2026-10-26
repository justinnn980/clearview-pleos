package ai.pleos.playground.clearview

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var controller: ClearViewController
    private lateinit var statusCanvas: StatusCanvasView

    private lateinit var notificationDot: View
    private lateinit var notificationTitle: TextView
    private lateinit var notificationSubtitle: TextView
    private lateinit var applyButton: Button

    private val modeButtons = mutableListOf<TextView>()

    private var selectedFogType: FogType = FogType.INSIDE_FOG
    private var isApplied: Boolean = false

    private val defaultSnapshot = EnvironmentSnapshot(
        indoorTempC = 22.0f,
        indoorHumidityPct = 80.0f,
        outdoorTempC = 3.0f,
        outdoorHumidityPct = 65.0f,
        glassSurfaceTempC = 7.0f,
        isParked = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = ClearViewController(
            context = this,
            onSdkLog = { },
            onCapabilitiesUpdated = { }
        )

        setContentView(buildThreeElementUi())
        selectScenario(FogType.INSIDE_FOG, autoApply = false)
    }

    /**
     * Strictly 3 user-facing elements on a single non-scrolling screen:
     * 1. 상태 (Intuitive Visual Status Canvas)
     * 2. 알림 (1-Line Notification)
     * 3. 적용 (1-Touch Apply Button)
     */
    private fun buildThreeElementUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(dp(20), dp(14), dp(20), dp(16))
        }

        // 1. 상태 (Visual Status Screen — ~75% height)
        val statusFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        statusCanvas = StatusCanvasView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        statusFrame.addView(statusCanvas)

        // Subtle top-right state switcher pills overlaid on the corner of the Status Canvas
        val pillBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedRect(Color.argb(190, 15, 23, 42), dp(16), Color.parseColor("#334155"), dp(1))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(12)
                rightMargin = dp(14)
            }
        }

        val modes = listOf(
            "안쪽 김서림" to FogType.INSIDE_FOG,
            "바깥쪽 물기" to FogType.OUTSIDE_CONDENSATION,
            "카메라 미러" to FogType.DSM_CAMERA_FOG
        )

        modes.forEachIndexed { index, (label, fogType) ->
            val pill = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectScenario(fogType, autoApply = false)
                }
            }
            modeButtons.add(pill)
            pillBar.addView(pill)
        }

        statusFrame.addView(pillBar)
        root.addView(statusFrame)

        // Bottom Bar combining: 2. 알림 (Notification) + 3. 적용 (Apply Action)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(Color.WHITE, dp(18), Color.parseColor("#E2E8F0"), dp(1))
            setPadding(dp(20), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        notificationDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                rightMargin = dp(14)
            }
            background = roundedRect(Color.parseColor("#2563EB"), dp(6))
        }

        // 2. 알림 (Notification Text Column)
        val notificationCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(16)
            }
        }

        notificationTitle = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            maxLines = 1
        }

        notificationSubtitle = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#64748B"))
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        }

        notificationCol.addView(notificationTitle)
        notificationCol.addView(notificationSubtitle)

        // 3. 적용 (Single Action Button)
        applyButton = Button(this).apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(26), dp(14), dp(26), dp(14))
            setOnClickListener {
                onApplyClicked()
            }
        }

        bottomBar.addView(notificationDot)
        bottomBar.addView(notificationCol)
        bottomBar.addView(applyButton)

        root.addView(bottomBar)
        return root
    }

    private fun selectScenario(fogType: FogType, autoApply: Boolean) {
        selectedFogType = fogType
        isApplied = autoApply

        val activeIndex = when (fogType) {
            FogType.INSIDE_FOG -> 0
            FogType.OUTSIDE_CONDENSATION -> 1
            FogType.DSM_CAMERA_FOG -> 2
        }

        modeButtons.forEachIndexed { idx, tv ->
            if (idx == activeIndex) {
                tv.setTextColor(Color.WHITE)
                tv.background = roundedRect(Color.parseColor("#2563EB"), dp(12))
            } else {
                tv.setTextColor(Color.parseColor("#94A3B8"))
                tv.background = null
            }
        }

        val visualState = when (fogType) {
            FogType.INSIDE_FOG -> StatusCanvasView.VisualState.INSIDE_FOG
            FogType.OUTSIDE_CONDENSATION -> StatusCanvasView.VisualState.OUTSIDE_CONDENSATION
            FogType.DSM_CAMERA_FOG -> StatusCanvasView.VisualState.DSM_CAMERA_FOG
        }
        statusCanvas.setState(visualState, applying = false)

        when (fogType) {
            FogType.INSIDE_FOG -> {
                updateNotificationAndButton(
                    dotHex = "#2563EB",
                    title = "알림 · 앞유리 안쪽에 습기가 찼어요",
                    subtitle = "전면 제습(FRONT)과 외기 유입으로 빠르게 제거합니다",
                    buttonText = "적용",
                    buttonHex = "#2563EB"
                )
            }
            FogType.OUTSIDE_CONDENSATION -> {
                updateNotificationAndButton(
                    dotHex = "#D97706",
                    title = "알림 · 앞유리 바깥쪽에 물기가 맺혔어요",
                    subtitle = "유리 찬 바람을 차단하고 와이퍼를 1회 작동합니다",
                    buttonText = "적용",
                    buttonHex = "#D97706"
                )
            }
            FogType.DSM_CAMERA_FOG -> {
                updateNotificationAndButton(
                    dotHex = "#059669",
                    title = "알림 · 카메라 미러 렌즈에 김이 서렸어요",
                    subtitle = "양쪽 미러 하단 렌즈 열선을 즉시 가동합니다",
                    buttonText = "적용",
                    buttonHex = "#059669"
                )
            }
        }
    }

    private fun onApplyClicked() {
        if (!isApplied) {
            isApplied = true
            val resolution = controller.resolveFogging(selectedFogType, defaultSnapshot)

            val visualState = when (selectedFogType) {
                FogType.INSIDE_FOG -> StatusCanvasView.VisualState.INSIDE_FOG
                FogType.OUTSIDE_CONDENSATION -> StatusCanvasView.VisualState.OUTSIDE_CONDENSATION
                FogType.DSM_CAMERA_FOG -> StatusCanvasView.VisualState.DSM_CAMERA_FOG
            }
            statusCanvas.setState(visualState, applying = true)

            updateNotificationAndButton(
                dotHex = "#10B981",
                title = "적용 완료 · ${resolution.toastHeadline}",
                subtitle = "시야가 깨끗해지면 원래 공조 상태로 자동 복귀합니다",
                buttonText = "해제",
                buttonHex = "#334155"
            )
        } else {
            isApplied = false
            controller.restoreComfortMode()
            statusCanvas.setState(StatusCanvasView.VisualState.CLEAR, applying = false)
            updateNotificationAndButton(
                dotHex = "#10B981",
                title = "알림 · 모든 유리와 카메라 미러 시야가 깨끗합니다",
                subtitle = "기존 공조 설정(AUTO)으로 복귀했습니다",
                buttonText = "다시 점검",
                buttonHex = "#2563EB"
            )
        }
    }

    private fun updateNotificationAndButton(
        dotHex: String,
        title: String,
        subtitle: String,
        buttonText: String,
        buttonHex: String
    ) {
        notificationDot.background = roundedRect(Color.parseColor(dotHex), dp(6))
        notificationTitle.text = title
        notificationSubtitle.text = subtitle
        applyButton.text = buttonText
        applyButton.background = roundedRect(Color.parseColor(buttonHex), dp(14))
    }

    private fun roundedRect(
        fillColor: Int,
        radiusPx: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(fillColor)
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
