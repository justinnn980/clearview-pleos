package ai.pleos.playground.clearview

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

class MainActivity : Activity() {

    private lateinit var controller: ClearViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = ClearViewController(
            context = this,
            onSdkLog = { },
            onCapabilitiesUpdated = { }
        )

        val hudView = HolographicHudView(
            context = this,
            onApplyCare = { frontIn, frontOut, sideMirror, rearGlass ->
                controller.applyAllAroundCare(
                    frontInside = frontIn,
                    frontOutside = frontOut,
                    dsmMirror = sideMirror,
                    rearGlass = rearGlass
                )
            },
            onRestoreCare = {
                controller.restoreComfortMode()
            }
        )

        setContentView(hudView)
        enableImmersiveCockpitMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveCockpitMode()
        }
    }

    private fun enableImmersiveCockpitMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { ctrl ->
                    ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    ctrl.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            }
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}

