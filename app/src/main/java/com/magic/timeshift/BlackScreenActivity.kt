package com.magic.timeshift

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
    private lateinit var statusText: TextView
    private val client = OkHttpClient()
    private var polling: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("timeshift_prefs", MODE_PRIVATE)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        statusText = TextView(this)
        statusText.setTextColor(Color.DKGRAY)
        statusText.textSize = 12f
        statusText.setPadding(24, 24, 24, 24)
        root.addView(statusText)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.visibility = android.view.View.INVISIBLE
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        if (!isAccessibilityServiceEnabled()) {
            statusText.text = "Accessibility service is OFF.\nEnable TimeShift in Settings > Accessibility, then try again."
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
            return
        }

        statusText.text = "Waiting for update..."
        startPolling()
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
        val baselineValue = fetchCurrentValueSync(apiLink, apiKey)

        polling = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val request = Request.Builder().url(apiLink).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            if (json.has(apiKey)) {
                                val value = json.getString(apiKey)
                                if (value.isNotBlank() && value != baselineValue) {
                                    withContext(Dispatchers.Main) { performSearch(value) }
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (e: Exception) { /* keep retrying */ }
                delay(2000)
            }
        }
    }

    private fun fetchCurrentValueSync(apiLink: String, apiKey: String): String {
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
        runOnUiThread { statusText.text = "Searching: $term" }
        val query = URLEncoder.encode(term, "UTF-8")
        webView.visibility = android.view.View.VISIBLE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                Handler(Looper.getMainLooper()).postDelayed({ takeScreenshotAndBackdate() }, 1500)
            }
        }
        webView.loadUrl("https://www.google.com/search?q=$query")
    }

    private fun takeScreenshotAndBackdate() {
        val service = ScreenshotAccessibilityService.instance
        if (service == null) {
            runOnUiThread { Toast.makeText(this, "Accessibility service disconnected", Toast.LENGTH_LONG).show() }
            finishAndReturnHome()
            return
        }
        service.captureScreenshot { success ->
            if (!success) {
                runOnUiThread { Toast.makeText(this, "Screenshot failed", Toast.LENGTH_LONG).show() }
            }
            finishAndReturnHome()
        }
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
        } catch (e: Exception) { /* admin not enabled */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        polling?.cancel()
    }
}
