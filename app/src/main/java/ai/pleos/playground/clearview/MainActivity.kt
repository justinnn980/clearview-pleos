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
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var controller: ClearViewController
    private lateinit var statusCanvas: StatusCanvasView

    private lateinit var notificationDot: View
    private lateinit var notificationTitle: TextView
    private lateinit var notificationSubtitle: TextView
    private lateinit var applyButton: Button

    private var isApplied: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = ClearViewController(
            context = this,
            onSdkLog = { },
            onCapabilitiesUpdated = { }
        )

        setContentView(buildSingleScreenUi())
        refreshNotificationFromCanvas()
    }

    /**
     * Tab-free, single-screen UI with strictly 3 elements:
     * 1. 상태 (All-in-One Front / Side Camera Mirror / Rear Visual Canvas)
     * 2. 알림 (1-Line Status Notification)
     * 3. 적용 (1-Touch Apply Action Button)
     */
    private fun buildSingleScreenUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(dp(20), dp(14), dp(20), dp(16))
        }

        // 1. 상태 (All-Around Vehicle Visual Status Canvas — no tabs)
        statusCanvas = StatusCanvasView(this) {
            isApplied = false
            statusCanvas.setApplying(false)
            refreshNotificationFromCanvas()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(statusCanvas)

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

        // 2. 알림 (1-Line Notification)
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

    private fun refreshNotificationFromCanvas() {
        val zones = mutableListOf<String>()
        val actions = mutableListOf<String>()

        if (statusCanvas.frontInsideFog) {
            zones.add("앞유리(안쪽)")
            actions.add("전면 제습(FRONT)")
        }
        if (statusCanvas.frontOutsideWater) {
            zones.add("앞유리(바깥쪽)")
            actions.add("와이퍼 1회(MIST)")
        }
        if (statusCanvas.dsmMirrorFog) {
            zones.add("좌·우 카메라 미러")
            actions.add("미러 렌즈 열선")
        }
        if (statusCanvas.rearGlassFog) {
            zones.add("뒷유리")
            actions.add("뒷유리 열선(REAR)")
        }

        if (zones.isEmpty()) {
            updateNotificationAndButton(
                dotHex = "#10B981",
                title = "알림 · 앞유리 · 카메라 미러 · 뒷유리 시야가 모두 깨끗합니다",
                subtitle = "차량 그림을 터치해 구역별 상태를 점검할 수 있습니다",
                buttonText = "전체 점검",
                buttonHex = "#2563EB"
            )
        } else {
            updateNotificationAndButton(
                dotHex = "#2563EB",
                title = "알림 · ${zones.joinToString(" · ")}에 김서림·물기가 감지되었어요",
                subtitle = "${actions.joinToString(" + ")}을 동시에 자동 작동합니다",
                buttonText = "적용",
                buttonHex = "#2563EB"
            )
        }
    }

    private fun onApplyClicked() {
        val anyActive = statusCanvas.frontInsideFog ||
            statusCanvas.frontOutsideWater ||
            statusCanvas.dsmMirrorFog ||
            statusCanvas.rearGlassFog

        if (!anyActive) {
            statusCanvas.resetAllZonesActive()
            refreshNotificationFromCanvas()
            return
        }

        if (!isApplied) {
            isApplied = true
            val summary = controller.applyAllAroundCare(
                frontInside = statusCanvas.frontInsideFog,
                frontOutside = statusCanvas.frontOutsideWater,
                dsmMirror = statusCanvas.dsmMirrorFog,
                rearGlass = statusCanvas.rearGlassFog
            )
            statusCanvas.setApplying(true)
            updateNotificationAndButton(
                dotHex = "#10B981",
                title = "적용 완료 · $summary",
                subtitle = "시야가 확보되면 기존 오토(AUTO) 공조 상태로 자동 복귀합니다",
                buttonText = "완료",
                buttonHex = "#059669"
            )
        } else {
            isApplied = false
            controller.restoreComfortMode()
            statusCanvas.frontInsideFog = false
            statusCanvas.frontOutsideWater = false
            statusCanvas.dsmMirrorFog = false
            statusCanvas.rearGlassFog = false
            statusCanvas.setApplying(false)
            refreshNotificationFromCanvas()
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

