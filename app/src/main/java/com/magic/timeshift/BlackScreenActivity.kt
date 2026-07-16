package com.magic.timeshift

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class BlackScreenActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private val client = OkHttpClient()
    private var polling: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("timeshift_prefs", MODE_PRIVATE)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.visibility = View.INVISIBLE
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2500)
            return
        }

        startPolling()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, ScreenshotAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    private fun startPolling() {
        val apiLink = prefs.getString("api_link", "") ?: ""
        val apiKey = prefs.getString("api_key", "value") ?: "value"

        polling = CoroutineScope(Dispatchers.IO).launch {
            val baselineValue = fetchCurrentValue(apiLink, apiKey)
            while (isActive) {
                delay(2000)
                val value = fetchCurrentValue(apiLink, apiKey)
                if (value.isNotBlank() && value != baselineValue) {
                    withContext(Dispatchers.Main) { performSearch(value) }
                    return@launch
                }
            }
        }
    }

    private fun fetchCurrentValue(apiLink: String, apiKey: String): String {
        return try {
            val request = Request.Builder().url(apiLink).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return ""
                val json = JSONObject(body)
                if (json.has(apiKey)) json.getString(apiKey) else ""
            }
        } catch (e: Exception) { "" }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun performSearch(term: String) {
        val query = URLEncoder.encode(term, "UTF-8")
        webView.visibility = View.VISIBLE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addFakeBrowserChrome()
                    Handler(Looper.getMainLooper()).postDelayed({ takeScreenshotAndBackdate() }, 400)
                }, 1200)
            }
        }
        webView.loadUrl("https://www.google.com/search?q=$query")
    }

    private fun addFakeBrowserChrome() {
        val darkTheme = prefs.getBoolean("dark_theme", false)
        val barBg = if (darkTheme) Color.parseColor("#2D2E30") else Color.WHITE
        val textColor = if (darkTheme) Color.LTGRAY else Color.DKGRAY
        val chipBg = if (darkTheme) Color.parseColor("#3C4043") else Color.parseColor("#F1F3F4")
        val density = resources.displayMetrics.density

        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setBackgroundColor(barBg)
        toolbar.setPadding((12 * density).toInt(), (28 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())

        val home = TextView(this)
        home.text = "⌂"
        home.textSize = 18f
        home.setTextColor(textColor)
        home.setPadding(0, 0, (16 * density).toInt(), 0)
        toolbar.addView(home)

        val addressBar = TextView(this)
        addressBar.text = "🔒 www.google.com"
        addressBar.textSize = 13f
        addressBar.setTextColor(textColor)
        addressBar.setBackgroundColor(chipBg)
        addressBar.setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
        val addressParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addressParams.marginEnd = (16 * density).toInt()
        toolbar.addView(addressBar, addressParams)

        val tabs = TextView(this)
        tabs.text = "1"
        tabs.textSize = 13f
        tabs.setTextColor(textColor)
        tabs.gravity = Gravity.CENTER
        val tabSize = (26 * density).toInt()
        val tabParams = LinearLayout.LayoutParams(tabSize, tabSize)
        tabParams.marginEnd = (16 * density).toInt()
        toolbar.addView(tabs, tabParams)

        val menu = TextView(this)
        menu.text = "⋮"
        menu.textSize = 18f
        menu.setTextColor(textColor)
        toolbar.addView(menu)

        val toolbarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        toolbarParams.gravity = Gravity.TOP
        root.addView(toolbar, toolbarParams)
    }

    private fun takeScreenshotAndBackdate() {
        val service = ScreenshotAccessibilityService.instance
        if (service == null) {
            finishAndReturnHome()
            return
        }
        service.captureScreenshot { success ->
            if (success) vibrateDone()
            finishAndReturnHome()
        }
    }

    private fun vibrateDone() {
        if (!prefs.getBoolean("vibrate_on_complete", true)) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 100, 120, 100, 120, 100)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun finishAndReturnHome() {
        polling?.cancel()
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)

        if (prefs.getBoolean("auto_lock", true)) {
            Handler(Looper.getMainLooper()).postDelayed({ lockPhone(); finish() }, 500)
        } else {
            finish()
        }
    }

    private fun lockPhone() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) dpm.lockNow()
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        polling?.cancel()
    }
}
