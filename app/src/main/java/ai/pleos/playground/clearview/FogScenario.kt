package ai.pleos.playground.clearview

import kotlin.math.ln

enum class FogType(
    val title: String,
    val subtitle: String,
    val badgeText: String
) {
    INSIDE_FOG(
        title = "유리 안쪽 김서림·서리",
        subtitle = "겨울철·우천 시 실내 습기가 차가운 유리 안쪽에 응결",
        badgeText = "원인: 실내 습도 과다 (안쪽)"
    ),
    OUTSIDE_CONDENSATION(
        title = "유리 바깥쪽 결로 (에어컨 역효과)",
        subtitle = "여름철 강하게 튼 에어컨 냉기로 유리 외벽에 외부 습기 응결",
        badgeText = "원인: 유리 과냉각 (바깥쪽)"
    ),
    DSM_CAMERA_FOG(
        title = "카메라 사이드미러(DSM) 렌즈 김서림",
        subtitle = "외부 돌출형 카메라 하우징 렌즈 결로 및 손이 닿지 않는 사각지대",
        badgeText = "원인: DSM 렌즈 결로 (외부)"
    )
}

data class EnvironmentSnapshot(
    val indoorTempC: Float,
    val indoorHumidityPct: Float,
    val outdoorTempC: Float,
    val outdoorHumidityPct: Float,
    val glassSurfaceTempC: Float,
    val isParked: Boolean
) {
    val indoorDewPointC: Float
        get() = calculateDewPoint(indoorTempC, indoorHumidityPct)

    val outdoorDewPointC: Float
        get() = calculateDewPoint(outdoorTempC, outdoorHumidityPct)

    companion object {
        /**
         * Magnus-Tetens formula for dew point temperature (°C)
         */
        fun calculateDewPoint(tempC: Float, relativeHumidityPct: Float): Float {
            val rh = relativeHumidityPct.coerceIn(1f, 100f)
            val b = 17.62f
            val c = 243.12f
            val gamma = (b * tempC) / (c + tempC) + ln(rh / 100f)
            return (c * gamma) / (b - gamma)
        }
    }
}

data class HardwareCapabilities(
    val sdkConnected: Boolean = false,
    val hasFrontDefroster: Boolean = true,
    val hasMirrorHeater: Boolean = true,
    val hasAutoWiper: Boolean = true,
    val hasHumiditySensor: Boolean = true
)

data class ActionResolution(
    val fogType: FogType,
    val toastHeadline: String,
    val voiceBriefing: String,
    val executedControls: List<String>,
    val manualCoachingSteps: List<String>,
    val showDsmLensGuide: Boolean
)

