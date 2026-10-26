package ai.pleos.playground.clearview

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView

class MainActivity : Activity() {

    private lateinit var controller: ClearViewController
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableImmersiveCockpitMode()

        controller = ClearViewController(
            context = this,
            onSdkLog = { },
            onCapabilitiesUpdated = { }
        )

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#040811"))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            addJavascriptInterface(HudBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/hud.html")
        }

        setContentView(webView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveCockpitMode()
        }
    }

    private fun enableImmersiveCockpitMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
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
    }

    inner class HudBridge {
        @JavascriptInterface
        fun onApplyCare(
            frontInside: Boolean,
            frontOutside: Boolean,
            sideMirror: Boolean,
            rearGlass: Boolean
        ) {
            runOnUiThread {
                controller.applyAllAroundCare(
                    frontInside = frontInside,
                    frontOutside = frontOutside,
                    dsmMirror = sideMirror,
                    rearGlass = rearGlass
                )
            }
        }

        @JavascriptInterface
        fun onRestoreCare() {
            runOnUiThread {
                controller.restoreComfortMode()
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        controller.release()
        super.onDestroy()
    }
}
