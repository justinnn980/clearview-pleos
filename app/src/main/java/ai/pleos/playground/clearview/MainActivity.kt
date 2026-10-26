package ai.pleos.playground.clearview

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var controller: ClearViewController

    private lateinit var toastBannerCard: LinearLayout
    private lateinit var toastDotView: View
    private lateinit var toastTitleText: TextView
    private lateinit var toastSubText: TextView

    private lateinit var envSummaryText: TextView
    private lateinit var capabilityBadgeText: TextView
    private lateinit var executedListLayout: LinearLayout
    private lateinit var coachingCard: LinearLayout
    private lateinit var coachingText: TextView
    private lateinit var dsmGuideCard: LinearLayout
    private lateinit var dsmGuideStatusText: TextView
    private lateinit var sdkLogText: TextView

    private val sdkLogs = ArrayDeque<String>()
    private var autoCareEnabled = true
    private var fullTrimSupported = true
    private var isParkedGear = true

    private var currentSnapshot = EnvironmentSnapshot(
        indoorTempC = 22.0f,
        indoorHumidityPct = 78.0f,
        outdoorTempC = 3.0f,
        outdoorHumidityPct = 65.0f,
        glassSurfaceTempC = 8.0f,
        isParked = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = buildMinimalistUi()
        setContentView(rootView)

        controller = ClearViewController(
            context = this,
            onSdkLog = { line ->
                runOnUiThread {
                    if (sdkLogs.size >= 8) sdkLogs.removeFirst()
                    sdkLogs.addLast(line)
                    sdkLogText.text = sdkLogs.joinToString("\n")
                }
            },
            onCapabilitiesUpdated = { caps ->
                runOnUiThread {
                    updateCapabilityBadge(caps)
                }
            }
        )

        updateEnvironmentSummary()
        updateCapabilityBadge(controller.capabilities)
    }

    private fun buildMinimalistUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(24)
            val padV = dp(20)
            setPadding(padH, padV, padH, padV)
        }
        scroll.addView(container)

        // 1. Header Row (Title + Auto-Care Switch)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appTitle = TextView(this).apply {
            text = "클리어뷰 (ClearView)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }

        val appSub = TextView(this).apply {
            text = "유리 안·밖 김서림 및 카메라 사이드미러(DSM) 원터치·자동 시야 케어"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#64748B"))
        }

        titleCol.addView(appTitle)
        titleCol.addView(appSub)

        val autoCareSwitch = Switch(this).apply {
            text = "자동 감지·제어  "
            isChecked = true
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setOnCheckedChangeListener { _, checked ->
                autoCareEnabled = checked
                toastSubText.text = if (checked) {
                    "자동 케어 ON — 실내·외 이슬점 도달 시 차가 스스로 공조·열선·와이퍼를 제어합니다."
                } else {
                    "수동 원터치 모드 — 시야가 흐려질 때 [앞이 안 보여요] 버튼을 눌러 주세요."
                }
            }
        }

        headerRow.addView(titleCol)
        headerRow.addView(autoCareSwitch)
        container.addView(headerRow)

        container.addView(space(14))

        // 2. Top Minimalist 1-Line Toast Banner (Pleos Connect Style)
        toastBannerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(Color.parseColor("#FFFFFF"), dp(14), Color.parseColor("#CBD5E1"), dp(1))
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        toastDotView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                rightMargin = dp(14)
            }
            background = roundedRect(Color.parseColor("#2563EB"), dp(6))
        }

        val toastTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        toastTitleText = TextView(this).apply {
            text = "시야 케어 대기 중 — [앞이 안 보여요] 또는 아래 상황 카드를 눌러 실행하세요"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        toastSubText = TextView(this).apply {
            text = "안쪽 김서림(제습)과 바깥쪽 결로(와이퍼)를 구분하고 카메라 미러 열선을 동시에 제어합니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#475569"))
        }

        toastTextCol.addView(toastTitleText)
        toastTextCol.addView(toastSubText)

        val restoreBtn = Button(this).apply {
            text = "원래대로"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#334155"))
            background = roundedRect(Color.parseColor("#F1F5F9"), dp(8), Color.parseColor("#CBD5E1"), dp(1))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                val msg = controller.restoreComfortMode()
                updateToastBanner("#475569", msg, "제습·열선 자동 종료 및 이전 공조 설정(AUTO) 복귀 완료")
                dsmGuideCard.visibility = View.GONE
                coachingCard.visibility = View.GONE
            }
        }

        toastBannerCard.addView(toastDotView)
        toastBannerCard.addView(toastTextCol)
        toastBannerCard.addView(restoreBtn)
        container.addView(toastBannerCard)

        container.addView(space(14))

        // 3. Primary One-Touch Button ("[앞이 안 보여요 — 원터치 시야 복구]")
        val panicButton = Button(this).apply {
            text = "앞이 안 보여요 — 원터치 시야 즉시 복구"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedRect(Color.parseColor("#2563EB"), dp(14))
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener {
                val detected = controller.diagnoseScenario(currentSnapshot)
                applyScenarioResolution(detected)
            }
        }
        container.addView(panicButton)

        container.addView(space(16))

        // 4. Scenario Trigger Section (3 Minimalist Cards for Evaluators)
        val sectionTitle = TextView(this).apply {
            text = "상황별 시나리오 검증 (탭하여 즉시 테스트)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#334155"))
        }
        container.addView(sectionTitle)
        container.addView(space(8))

        val scenarioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        scenarioRow.addView(
            createScenarioCard(
                badge = "시나리오 ① 겨울·우천",
                title = "유리 안쪽 김서림",
                desc = "전면 제습(FRONT) + A/C + 외기 유입 + 미러 열선",
                accentHex = "#2563EB",
                weight = 1f,
                rightMarginDp = 8
            ) {
                currentSnapshot = EnvironmentSnapshot(
                    indoorTempC = 22.0f,
                    indoorHumidityPct = 82.0f,
                    outdoorTempC = 2.0f,
                    outdoorHumidityPct = 65.0f,
                    glassSurfaceTempC = 6.5f,
                    isParked = isParkedGear
                )
                updateEnvironmentSummary()
                applyScenarioResolution(FogType.INSIDE_FOG)
            }
        )

        scenarioRow.addView(
            createScenarioCard(
                badge = "시나리오 ② 여름 열대야",
                title = "유리 바깥쪽 결로",
                desc = "유리 냉기 송풍 차단 + 온도 상향 + 와이퍼 1회(MIST)",
                accentHex = "#D97706",
                weight = 1f,
                rightMarginDp = 8
            ) {
                currentSnapshot = EnvironmentSnapshot(
                    indoorTempC = 19.5f,
                    indoorHumidityPct = 45.0f,
                    outdoorTempC = 30.0f,
                    outdoorHumidityPct = 88.0f,
                    glassSurfaceTempC = 21.0f,
                    isParked = isParkedGear
                )
                updateEnvironmentSummary()
                applyScenarioResolution(FogType.OUTSIDE_CONDENSATION)
            }
        )

        scenarioRow.addView(
            createScenarioCard(
                badge = "시나리오 ③ 카메라 미러",
                title = "DSM 렌즈 김서림",
                desc = "미러 하우징 열선 가동 + P단 정차 시 렌즈 위치 안내",
                accentHex = "#059669",
                weight = 1f,
                rightMarginDp = 0
            ) {
                currentSnapshot = EnvironmentSnapshot(
                    indoorTempC = 21.0f,
                    indoorHumidityPct = 76.0f,
                    outdoorTempC = 5.0f,
                    outdoorHumidityPct = 85.0f,
                    glassSurfaceTempC = 7.0f,
                    isParked = isParkedGear
                )
                updateEnvironmentSummary()
                applyScenarioResolution(FogType.DSM_CAMERA_FOG)
            }
        )

        container.addView(scenarioRow)

        container.addView(space(14))

        // 5. Hardware Capability & Gear Mode Bar (Addresses non-equipped vehicles!)
        val hwCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, dp(12), Color.parseColor("#E2E8F0"), dp(1))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val hwTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        capabilityBadgeText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = "하드웨어 사양: 풀옵션 트림 (전동 제어 지원)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }

        val trimToggleBtn = Button(this).apply {
            text = "트림 전환: 풀옵션 ↔ 기본형(미지원 폴백)"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#1E40AF"))
            background = roundedRect(Color.parseColor("#EFF6FF"), dp(8), Color.parseColor("#BFDBFE"), dp(1))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                fullTrimSupported = !fullTrimSupported
                controller.setSimulatedTrimMode(fullTrimSupported)
            }
        }

        val gearToggleBtn = Button(this).apply {
            text = "기어: P (정차)"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#065F46"))
            background = roundedRect(Color.parseColor("#ECFDF5"), dp(8), Color.parseColor("#A7F3D0"), dp(1))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(8)
            }
            setOnClickListener {
                isParkedGear = !isParkedGear
                currentSnapshot = currentSnapshot.copy(isParked = isParkedGear)
                text = if (isParkedGear) "기어: P (정차)" else "기어: D (주행)"
                updateDsmLensGuideVisibility(dsmGuideCard.visibility == View.VISIBLE)
            }
        }

        hwTopRow.addView(capabilityBadgeText)
        hwTopRow.addView(trimToggleBtn)
        hwTopRow.addView(gearToggleBtn)

        envSummaryText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(6), 0, 0)
        }

        hwCard.addView(hwTopRow)
        hwCard.addView(envSummaryText)
        container.addView(hwCard)

        container.addView(space(12))

        // 6. Executed Controls & Fallback Coaching Card
        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, dp(12), Color.parseColor("#E2E8F0"), dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val resultHeader = TextView(this).apply {
            text = "실행된 차량 제어 및 조치 내역"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }
        resultCard.addView(resultHeader)

        executedListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        val emptyHint = TextView(this).apply {
            text = "• 상단 [앞이 안 보여요] 버튼 또는 시나리오 ①·②·③ 카드를 선택하면 제어 내역이 표시됩니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#64748B"))
        }
        executedListLayout.addView(emptyHint)
        resultCard.addView(executedListLayout)

        // Manual Coaching Fallback Box (shown when vehicle trim lacks hardware)
        coachingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedRect(Color.parseColor("#FFFBEB"), dp(10), Color.parseColor("#FDE68A"), dp(1))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }
        val coachingTitle = TextView(this).apply {
            text = "운전자 1단계 조작 코칭 (일부 하드웨어 미지원 차량 폴백)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#B45309"))
        }
        coachingText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#92400E"))
            setPadding(0, dp(4), 0, 0)
        }
        coachingCard.addView(coachingTitle)
        coachingCard.addView(coachingText)
        resultCard.addView(coachingCard)

        container.addView(resultCard)

        container.addView(space(12))

        // 7. DSM Camera Lens Physical Location Guide Card (Shown on DSM scenario)
        dsmGuideCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedRect(Color.parseColor("#ECFDF5"), dp(12), Color.parseColor("#6EE7B7"), dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val dsmTitle = TextView(this).apply {
            text = "카메라 사이드미러(DSM) 렌즈 실제 위치 가이드"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#065F46"))
        }

        dsmGuideStatusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#047857"))
            setPadding(0, dp(4), 0, 0)
        }

        dsmGuideCard.addView(dsmTitle)
        dsmGuideCard.addView(dsmGuideStatusText)
        container.addView(dsmGuideCard)

        container.addView(space(12))

        // 8. Pleos Connect Vehicle SDK Live Call Log
        val logCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.parseColor("#0F172A"), dp(12))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val logHeader = TextView(this).apply {
            text = "Pleos Connect Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2) 호출 로그"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#93C5FD"))
        }

        sdkLogText = TextView(this).apply {
            text = "초기화 중..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(6), 0, 0)
        }

        logCard.addView(logHeader)
        logCard.addView(sdkLogText)
        container.addView(logCard)

        return scroll
    }

    private fun createScenarioCard(
        badge: String,
        title: String,
        desc: String,
        accentHex: String,
        weight: Float,
        rightMarginDp: Int,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, dp(12), Color.parseColor("#CBD5E1"), dp(1))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                if (rightMarginDp > 0) rightMargin = dp(rightMarginDp)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val badgeTv = TextView(this).apply {
            text = badge
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(accentHex))
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(4), 0, dp(4))
        }

        val descTv = TextView(this).apply {
            text = desc
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#475569"))
        }

        card.addView(badgeTv)
        card.addView(titleTv)
        card.addView(descTv)
        return card
    }

    private fun applyScenarioResolution(fogType: FogType) {
        val resolution = controller.resolveFogging(fogType, currentSnapshot)

        val accentColor = when (fogType) {
            FogType.INSIDE_FOG -> "#2563EB"
            FogType.OUTSIDE_CONDENSATION -> "#D97706"
            FogType.DSM_CAMERA_FOG -> "#059669"
        }

        updateToastBanner(accentColor, resolution.toastHeadline, resolution.voiceBriefing)

        executedListLayout.removeAllViews()
        for (line in resolution.executedControls) {
            val tv = TextView(this).apply {
                text = "✓ $line"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#1E293B"))
                setPadding(0, dp(2), 0, dp(2))
            }
            executedListLayout.addView(tv)
        }

        if (resolution.manualCoachingSteps.isNotEmpty()) {
            coachingCard.visibility = View.VISIBLE
            coachingText.text = resolution.manualCoachingSteps.joinToString("\n") { "👉 $it" }
        } else {
            coachingCard.visibility = View.GONE
        }

        updateDsmLensGuideVisibility(resolution.showDsmLensGuide)
    }

    private fun updateDsmLensGuideVisibility(show: Boolean) {
        if (!show) {
            dsmGuideCard.visibility = View.GONE
            return
        }
        dsmGuideCard.visibility = View.VISIBLE
        dsmGuideStatusText.text = if (isParkedGear) {
            "• 현재 기어 [P 정차] 확인됨: 카메라 렌즈는 일반 거울 위치가 아닌 [사이드미러 암(돌출부) 바깥쪽 끝단 아래]에 위치해 있습니다.\n" +
                "• 열선 작동 후에도 물방울이 남아 있다면 해당 하단 렌즈 부위를 부드러운 천으로 가볍게 닦아 주세요."
        } else {
            "• 현재 기어 [D 주행 중]: 주행 중 창문을 내리고 손을 뻗어 렌즈를 찾는 행위는 매우 위험합니다.\n" +
                "• 현재 미러 하우징 열선이 가동 중이며, 정차(P단) 시 정확한 렌즈 물리적 위치 안내가 표시됩니다."
        }
    }

    private fun updateToastBanner(accentHex: String, headline: String, subline: String) {
        toastDotView.background = roundedRect(Color.parseColor(accentHex), dp(6))
        toastTitleText.text = headline
        toastSubText.text = subline
    }

    private fun updateEnvironmentSummary() {
        val inDew = String.format(Locale.US, "%.1f", currentSnapshot.indoorDewPointC)
        val outDew = String.format(Locale.US, "%.1f", currentSnapshot.outdoorDewPointC)
        envSummaryText.text =
            "실내 ${currentSnapshot.indoorTempC.toInt()}℃ (습도 ${currentSnapshot.indoorHumidityPct.toInt()}%, 이슬점 ${inDew}℃)  |  " +
                "실외 ${currentSnapshot.outdoorTempC.toInt()}℃ (습도 ${currentSnapshot.outdoorHumidityPct.toInt()}%, 이슬점 ${outDew}℃)  |  " +
                "유리 표면 ${currentSnapshot.glassSurfaceTempC}℃"
    }

    private fun updateCapabilityBadge(caps: HardwareCapabilities) {
        capabilityBadgeText.text = if (caps.hasAutoWiper && caps.hasMirrorHeater && caps.hasHumiditySensor) {
            "하드웨어 사양: 풀옵션 트림 (전동 와이퍼·미러 열선·습도 센서 자동 제어)"
        } else {
            "하드웨어 사양: 기본형 트림 (미지원 하드웨어 감지 → 기상 데이터 + 음성·화면 코칭 폴백 동작)"
        }
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

    private fun space(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}

