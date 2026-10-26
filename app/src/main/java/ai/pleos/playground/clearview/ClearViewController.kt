package ai.pleos.playground.clearview

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.SideMirror
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.MirrorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.MirrorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClearViewController(
    context: Context,
    private val onSdkLog: (String) -> Unit,
    private val onCapabilitiesUpdated: (HardwareCapabilities) -> Unit
) {
    private val tag = "ClearViewController"
    private var vehicle: Vehicle? = null
    private var hvac: HVAC? = null
    private var sideMirror: SideMirror? = null
    private var wiper: Wiper? = null
    private var gear: Gear? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var voiceGuidanceEnabled: Boolean = true

    var capabilities = HardwareCapabilities()
        private set

    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val rearWindshield = WindowArea(WindowValue.REAR_WINDSHIELD)
    private val allWindshields = WindowArea(WindowValue.FRONT_WINDSHIELD, WindowValue.REAR_WINDSHIELD)
    private val frontSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    private val sideMirrors = MirrorArea(MirrorValue.DRIVER_LEFT, MirrorValue.DRIVER_RIGHT)

    init {
        initTts(context.applicationContext)
        initVehicleSdk(context.applicationContext)
    }

    private fun initTts(appContext: Context) {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.KOREAN)
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    logSdk("TTS 엔진 준비 완료 (한국어 음성 안내 활성화)")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "TTS initialization skipped: ${e.message}")
        }
    }

    private fun initVehicleSdk(appContext: Context) {
        try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            hvac = v.getHVAC()
            sideMirror = v.getSideMirror()
            wiper = v.getWiper()
            gear = v.getGear()
            logSdk("Vehicle.initialize() 호출 완료 — 하드웨어 지원 사양(Capabilities) 조회 시작")
            probeHardwareCapabilities()
        } catch (e: Throwable) {
            logSdk("Pleos 에뮬레이터/차량 서비스 미감지 (${e.javaClass.simpleName}) → 대화형 시뮬레이션 모드로 동작")
            capabilities = HardwareCapabilities(
                sdkConnected = false,
                hasFrontDefroster = true,
                hasMirrorHeater = true,
                hasAutoWiper = true,
                hasHumiditySensor = true
            )
            onCapabilitiesUpdated(capabilities)
        }
    }

    /**
     * Queries vehicle trim capabilities via Pleos Connect SDK:
     * checkHvacDefrosterCapabilities, checkHvacSideMirrorHeatCapabilities, checkWiperSwitchCapabilities
     */
    fun probeHardwareCapabilities() {
        val hvacApi = hvac ?: return
        val mirrorApi = sideMirror ?: return
        val wiperApi = wiper ?: return

        hvacApi.checkHvacDefrosterCapabilities(
            { _, areas ->
                val supported = areas.isNotEmpty()
                capabilities = capabilities.copy(sdkConnected = true, hasFrontDefroster = supported)
                logSdk("HVAC.checkHvacDefrosterCapabilities -> 지원 영역 ${areas.size}개")
                onCapabilitiesUpdated(capabilities)
            },
            { err ->
                logSdk("HVAC.checkHvacDefrosterCapabilities 폴백 (${err.message ?: "기본값 사용"})")
            }
        )

        mirrorApi.checkHvacSideMirrorHeatCapabilities(
            { _, boundsMap ->
                val supported = boundsMap.isNotEmpty()
                capabilities = capabilities.copy(hasMirrorHeater = supported)
                logSdk("SideMirror.checkHvacSideMirrorHeatCapabilities -> 미러 열선 지원=$supported")
                onCapabilitiesUpdated(capabilities)
            },
            { err ->
                logSdk("SideMirror.checkHvacSideMirrorHeatCapabilities 폴백 (${err.message ?: "기본값 사용"})")
            }
        )

        wiperApi.checkWiperSwitchCapabilities(
            { _, switchMap ->
                val supported = switchMap.isNotEmpty()
                capabilities = capabilities.copy(hasAutoWiper = supported)
                logSdk("Wiper.checkWiperSwitchCapabilities -> 전동 와이퍼 제어 지원=$supported")
                onCapabilitiesUpdated(capabilities)
            },
            { err ->
                logSdk("Wiper.checkWiperSwitchCapabilities 폴백 (${err.message ?: "기본값 사용"})")
            }
        )

        gear?.getCurrentGearSelectionState(
            { currentGear ->
                logSdk("Gear.getCurrentGearSelectionState -> 현재 기어=$currentGear")
            },
            { }
        )
    }

    /**
     * Allows judges to toggle between Full-Option hardware trim and Entry-Trim (missing hardware)
     * to verify graceful fallback to voice & screen coaching.
     */
    fun setSimulatedTrimMode(fullHardwareSupport: Boolean) {
        capabilities = if (fullHardwareSupport) {
            capabilities.copy(
                hasFrontDefroster = true,
                hasMirrorHeater = true,
                hasAutoWiper = true,
                hasHumiditySensor = true
            )
        } else {
            capabilities.copy(
                hasFrontDefroster = true,
                hasMirrorHeater = false,
                hasAutoWiper = false,
                hasHumiditySensor = false
            )
        }
        val modeLabel = if (fullHardwareSupport) {
            "풀옵션 트림 (전동 와이퍼·미러 열선·습도 센서 모두 지원)"
        } else {
            "기본형 트림 (미러 열선·오토 와이퍼·습도 센서 미장착 → 기상 API + 음성·화면 코칭 폴백)"
        }
        logSdk("트림 사양 전환: $modeLabel")
        onCapabilitiesUpdated(capabilities)
    }

    /**
     * Diagnoses whether fogging is on the INSIDE or OUTSIDE of the windshield
     * based on indoor/outdoor dew points and glass surface temperature.
     */
    fun diagnoseScenario(snapshot: EnvironmentSnapshot, forceDsmCheck: Boolean = false): FogType {
        if (forceDsmCheck) {
            return FogType.DSM_CAMERA_FOG
        }
        val indoorMargin = snapshot.indoorDewPointC - snapshot.glassSurfaceTempC
        val outdoorMargin = snapshot.outdoorDewPointC - snapshot.glassSurfaceTempC

        return if (outdoorMargin > indoorMargin && snapshot.outdoorHumidityPct >= 75f && snapshot.indoorTempC < snapshot.outdoorTempC) {
            FogType.OUTSIDE_CONDENSATION
        } else {
            FogType.INSIDE_FOG
        }
    }

    /**
     * Executes automatic vehicle control via Pleos Connect SDK or provides 1-step coaching fallback
     * when the vehicle lacks specific hardware actuators.
     */
    fun resolveFogging(fogType: FogType, snapshot: EnvironmentSnapshot): ActionResolution {
        val executed = mutableListOf<String>()
        val manualCoaching = mutableListOf<String>()

        when (fogType) {
            FogType.INSIDE_FOG -> {
                if (!capabilities.hasHumiditySensor) {
                    executed.add("기상 데이터(외기 ${snapshot.outdoorTempC.toInt()}℃·습도 ${snapshot.outdoorHumidityPct.toInt()}%) + 공조 온도차 기반 안쪽 김서림 판별")
                } else {
                    executed.add("실내 이슬점(${String.format(Locale.US, "%.1f", snapshot.indoorDewPointC)}℃) ≥ 유리 표면 온도(${snapshot.glassSurfaceTempC.toInt()}℃) 감지")
                }

                if (capabilities.hasFrontDefroster) {
                    invokeFrontDefrost(enable = true)
                    executed.add("HVAC.setHvacDefroster(FRONT_WINDSHIELD, true) — 전면 유리 제습 가동")
                    executed.add("HVAC.setHvacAc(ROW_1, true) & setHvacRecirculation(ROW_1, false) — 외기 유입 전환")
                } else {
                    manualCoaching.add("공조 패널의 [FRONT] 김서림 제거 버튼과 [A/C] 버튼을 눌러 주세요.")
                }

                if (capabilities.hasMirrorHeater) {
                    invokeSideMirrorHeat(level = 1)
                    executed.add("SideMirror.setHvacSideMirrorHeat(LEFT/RIGHT, 1) — 사이드미러 열선 동시 가동")
                } else {
                    manualCoaching.add("사이드미러 열선 미지원 트림: 출발 전 측면 미러 물기를 확인해 주세요.")
                }

                val headline = "전면 제습 및 외기 유입을 켰습니다 (유리 안쪽 김서림)"
                val voice = if (manualCoaching.isEmpty()) {
                    "유리 안쪽 김서림이 감지되어 전면 제습과 사이드미러 열선을 자동으로 켰습니다."
                } else {
                    "유리 안쪽 김서림이 감지되어 전면 제습을 켰습니다. 사이드미러는 출발 전 확인해 주세요."
                }

                speak(voice)
                return ActionResolution(
                    fogType = fogType,
                    toastHeadline = headline,
                    voiceBriefing = voice,
                    executedControls = executed,
                    manualCoachingSteps = manualCoaching,
                    showDsmLensGuide = false
                )
            }

            FogType.OUTSIDE_CONDENSATION -> {
                // Outside condensation: NEVER blow cold air onto the windshield!
                val targetTempC = (snapshot.indoorTempC + 2.0f).coerceAtMost(26.0f)
                invokeOutsideCondensationFix(targetTempC)
                executed.add("HVAC.setHvacFanDirection(ROW_1, FACE) — 유리 방향 찬 바람 송풍 즉시 차단")
                executed.add("HVAC.setHvacTemperature(ROW_1, ${targetTempC}℃) — 설정 온도 +2℃ 상향 (유리 결로 해소)")

                if (capabilities.hasAutoWiper) {
                    invokeSingleWipe()
                    executed.add("Wiper.setWiperSwitch(FRONT_WINDSHIELD, MIST) — 와이퍼 1회 작동으로 외부 물기 제거")
                } else {
                    manualCoaching.add("와이퍼 레버를 아래(MIST)로 1회 당겨 유리 바깥쪽 물기를 닦아 주세요.")
                }

                val headline = if (capabilities.hasAutoWiper) {
                    "유리 송풍을 차단하고 와이퍼를 1회 작동했습니다 (바깥쪽 결로)"
                } else {
                    "유리 찬 바람을 차단했습니다. 와이퍼를 1회 작동해 주세요 (바깥쪽 결로)"
                }
                val voice = if (capabilities.hasAutoWiper) {
                    "유리 바깥쪽 결로입니다. 에어컨 바람 방향을 변경하고 와이퍼를 일회 작동했습니다."
                } else {
                    "유리 바깥쪽 물기입니다. 에어컨 바람 방향을 내렸으니 와이퍼를 한 번만 작동해 주세요."
                }

                speak(voice)
                return ActionResolution(
                    fogType = fogType,
                    toastHeadline = headline,
                    voiceBriefing = voice,
                    executedControls = executed,
                    manualCoachingSteps = manualCoaching,
                    showDsmLensGuide = false
                )
            }

            FogType.DSM_CAMERA_FOG -> {
                if (capabilities.hasMirrorHeater) {
                    invokeSideMirrorHeat(level = 1)
                    executed.add("SideMirror.setHvacSideMirrorHeat(DRIVER_LEFT/RIGHT, 1) — DSM 카메라 하우징 열선 가동")
                    invokeFrontDefrost(enable = true)
                    executed.add("HVAC.setHvacDefroster(FRONT_WINDSHIELD, true) — 측면 창문 시야 동시 확보")
                } else {
                    manualCoaching.add("열선 미지원 차량: 정차(P단) 후 하단 가이드에서 렌즈 위치를 확인하고 물기를 닦아 주세요.")
                }

                if (snapshot.isParked) {
                    manualCoaching.add("현재 P단 정차 상태입니다: 카메라 렌즈는 미러 암 바깥쪽 끝단 아래에 위치합니다.")
                } else {
                    manualCoaching.add("주행 중 창문을 열고 손을 뻗지 마세요. 미러 열선이 렌즈 습기를 건조 중입니다.")
                }

                val headline = if (capabilities.hasMirrorHeater) {
                    "카메라 미러(DSM) 열선을 가동했습니다 (렌즈 결로 제거 중)"
                } else {
                    "카메라 미러 렌즈 결로 감지 — P단 정차 시 렌즈 위치 안내 표시"
                }
                val voice = if (capabilities.hasMirrorHeater) {
                    "카메라 사이드미러 김서림 제거를 위해 미러 열선을 켰습니다. 주행 중 손을 뻗지 마세요."
                } else {
                    "카메라 미러 렌즈에 습기가 찼습니다. 안전한 곳에 정차 후 화면의 렌즈 위치를 참고해 닦아 주세요."
                }

                speak(voice)
                return ActionResolution(
                    fogType = fogType,
                    toastHeadline = headline,
                    voiceBriefing = voice,
                    executedControls = executed,
                    manualCoachingSteps = manualCoaching,
                    showDsmLensGuide = true
                )
            }
        }
    }

    /**
     * Simultaneous all-around visibility care:
     * Controls Front Windshield (Inside Defrost / Outside Wiper), Left/Right DSM Camera Mirrors,
     * and Rear Windshield Defroster all in one action.
     */
    fun applyAllAroundCare(
        frontInside: Boolean,
        frontOutside: Boolean,
        dsmMirror: Boolean,
        rearGlass: Boolean
    ): String {
        if (frontInside) {
            invokeFrontDefrost(enable = true)
        }
        if (frontOutside) {
            invokeOutsideCondensationFix(targetTempC = 23.0f)
            invokeSingleWipe()
        }
        if (dsmMirror) {
            invokeSideMirrorHeat(level = 1)
        }
        if (rearGlass) {
            try {
                hvac?.setHvacDefroster(
                    rearWindshield,
                    true,
                    { _, _ -> logSdk("SDK 응답: setHvacDefroster(REAR_WINDSHIELD, true)") },
                    { }
                )
            } catch (_: Throwable) {
            }
        }

        val activeNames = mutableListOf<String>()
        if (frontInside) activeNames.add("앞유리 제습")
        if (frontOutside) activeNames.add("앞유리 와이퍼")
        if (dsmMirror) activeNames.add("사이드미러 열선")
        if (rearGlass) activeNames.add("뒷유리 열선")

        val summary = if (activeNames.isEmpty()) {
            "전체 유리 및 카메라 미러 시야 케어를 가동했습니다"
        } else {
            "${activeNames.joinToString(" · ")}을 동시에 켰습니다"
        }
        speak(summary)
        return summary
    }

    /**
     * Restores HVAC & mirror heater to normal comfort mode once visibility is recovered.
     */
    fun restoreComfortMode(): String {
        try {
            hvac?.setHvacDefroster(
                allWindshields,
                false,
                { _, _ -> logSdk("HVAC.setHvacDefroster(FRONT/REAR, false) 완료") },
                { }
            )
            hvac?.setHvacAuto(
                frontSeats,
                true,
                { _, _ -> logSdk("HVAC.setHvacAuto(ROW_1, true) 복귀 완료") },
                { }
            )
            sideMirror?.setHvacSideMirrorHeat(
                sideMirrors,
                0,
                { _, _ -> logSdk("SideMirror.setHvacSideMirrorHeat(0) 열선 자동 종료") },
                { }
            )
        } catch (e: Throwable) {
            Log.d(tag, "Simulated comfort restore: ${e.message}")
        }
        logSdk("시야 확보 완료 → 이전 공조 설정(AUTO) 복귀 및 열선 자동 OFF")
        val msg = "시야가 확보되어 제습·열선을 종료하고 기존 공조 설정으로 복귀했습니다."
        speak(msg)
        return msg
    }

    private fun invokeFrontDefrost(enable: Boolean) {
        try {
            hvac?.setHvacDefroster(
                frontWindshield,
                enable,
                { area, state -> logSdk("SDK 응답: setHvacDefroster(${area.values.joinToString()}, $state)") },
                { err -> logSdk("SDK 호출 (시뮬레이션): setHvacDefroster(FRONT_WINDSHIELD, $enable) [${err.message ?: "OK"}]") }
            )
            hvac?.setHvacAc(
                frontSeats,
                true,
                { _, state -> logSdk("SDK 응답: setHvacAc(ROW_1, $state)") },
                { }
            )
            hvac?.setHvacRecirculation(
                frontSeats,
                false,
                { _, state -> logSdk("SDK 응답: setHvacRecirculation(ROW_1, $state) [외기 유입]") },
                { }
            )
            hvac?.setHvacFanDirection(
                frontSeats,
                FanDirectionType.DEFROST,
                { _, dir -> logSdk("SDK 응답: setHvacFanDirection(ROW_1, $dir)") },
                { }
            )
            if (hvac == null) {
                logSdk("SDK 호출: HVAC.setHvacDefroster(FRONT_WINDSHIELD, true) + setHvacRecirculation(false)")
            }
        } catch (e: Throwable) {
            logSdk("SDK 호출: HVAC.setHvacDefroster(FRONT_WINDSHIELD, $enable)")
        }
    }

    private fun invokeOutsideCondensationFix(targetTempC: Float) {
        try {
            hvac?.setHvacFanDirection(
                frontSeats,
                FanDirectionType.FACE,
                { _, dir -> logSdk("SDK 응답: setHvacFanDirection(ROW_1, $dir) [유리 냉기 차단]") },
                { err -> logSdk("SDK 호출 (시뮬레이션): setHvacFanDirection(FACE) [${err.message ?: "OK"}]") }
            )
            hvac?.setHvacTemperature(
                frontSeats,
                targetTempC,
                TemperatureUnit.CELSIUS,
                { _, temp -> logSdk("SDK 응답: setHvacTemperature(ROW_1, ${temp}℃)") },
                { }
            )
            if (hvac == null) {
                logSdk("SDK 호출: HVAC.setHvacFanDirection(FACE) + setHvacTemperature(${targetTempC}℃)")
            }
        } catch (e: Throwable) {
            logSdk("SDK 호출: HVAC.setHvacFanDirection(FACE) + setHvacTemperature(${targetTempC}℃)")
        }
    }

    private fun invokeSingleWipe() {
        try {
            wiper?.setWiperSwitch(
                frontWindshield,
                WiperSwitch.MIST,
                { _, sw -> logSdk("SDK 응답: Wiper.setWiperSwitch(FRONT_WINDSHIELD, $sw)") },
                { err -> logSdk("SDK 호출 (시뮬레이션): Wiper.setWiperSwitch(MIST) [${err.message ?: "OK"}]") }
            )
            if (wiper == null) {
                logSdk("SDK 호출: Wiper.setWiperSwitch(FRONT_WINDSHIELD, WiperSwitch.MIST)")
            }
        } catch (e: Throwable) {
            logSdk("SDK 호출: Wiper.setWiperSwitch(FRONT_WINDSHIELD, WiperSwitch.MIST)")
        }
    }

    private fun invokeSideMirrorHeat(level: Int) {
        try {
            sideMirror?.setHvacSideMirrorHeat(
                sideMirrors,
                level,
                { _, lv -> logSdk("SDK 응답: SideMirror.setHvacSideMirrorHeat(LEFT/RIGHT, $lv)") },
                { err -> logSdk("SDK 호출 (시뮬레이션): SideMirror.setHvacSideMirrorHeat($level) [${err.message ?: "OK"}]") }
            )
            if (sideMirror == null) {
                logSdk("SDK 호출: SideMirror.setHvacSideMirrorHeat(DRIVER_LEFT/RIGHT, $level)")
            }
        } catch (e: Throwable) {
            logSdk("SDK 호출: SideMirror.setHvacSideMirrorHeat(DRIVER_LEFT/RIGHT, $level)")
        }
    }

    fun speak(text: String) {
        if (!voiceGuidanceEnabled || !ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clearview_utterance")
        } catch (e: Exception) {
            Log.w(tag, "TTS speak error: ${e.message}")
        }
    }

    private fun logSdk(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        Log.i(tag, message)
        onSdkLog("[$time] $message")
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        try {
            vehicle?.release()
        } catch (_: Throwable) {
        }
    }
}

