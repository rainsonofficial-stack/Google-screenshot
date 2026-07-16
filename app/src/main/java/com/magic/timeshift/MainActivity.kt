package com.magic.timeshift

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("timeshift_prefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_theme", false)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editApiLink = findViewById<EditText>(R.id.editApiLink)
        val editApiKey = findViewById<EditText>(R.id.editApiKey)
        val radioGroup = findViewById<RadioGroup>(R.id.radioTimeGroup)
        val editCustomDateTime = findViewById<EditText>(R.id.editCustomDateTime)
        val switchAutoLock = findViewById<Switch>(R.id.switchAutoLock)
        val switchDarkTheme = findViewById<Switch>(R.id.switchDarkTheme)
        val switchVibrate = findViewById<Switch>(R.id.switchVibrate)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibilitySettings)
        val btnDeviceAdmin = findViewById<Button>(R.id.btnDeviceAdminSettings)

        editApiLink.setText(prefs.getString("api_link", ""))
        editApiKey.setText(prefs.getString("api_key", "value"))
        switchAutoLock.isChecked = prefs.getBoolean("auto_lock", true)
        switchDarkTheme.isChecked = prefs.getBoolean("dark_theme", false)
        switchVibrate.isChecked = prefs.getBoolean("vibrate_on_complete", true)

        when (prefs.getString("time_setting", "3h")) {
            "3h" -> radioGroup.check(R.id.radio3h)
            "10h" -> radioGroup.check(R.id.radio10h)
            "24h" -> radioGroup.check(R.id.radio24h)
            "3d" -> radioGroup.check(R.id.radio3d)
            "custom" -> radioGroup.check(R.id.radioCustom)
        }
        editCustomDateTime.setText(prefs.getString("custom_datetime", ""))
        editCustomDateTime.visibility =
            if (radioGroup.checkedRadioButtonId == R.id.radioCustom) View.VISIBLE else View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            editCustomDateTime.visibility =
                if (checkedId == R.id.radioCustom) View.VISIBLE else View.GONE
        }

        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_theme", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this, MyDeviceAdminReceiver::class.java)
            )
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Needed to auto-lock the phone after the effect."
            )
            startActivity(intent)
        }

        btnConfirm.setOnClickListener {
            val timeKey = when (radioGroup.checkedRadioButtonId) {
                R.id.radio3h -> "3h"
                R.id.radio10h -> "10h"
                R.id.radio24h -> "24h"
                R.id.radio3d -> "3d"
                R.id.radioCustom -> "custom"
                else -> "3h"
            }
            prefs.edit()
                .putString("api_link", editApiLink.text.toString().trim())
                .putString("api_key", editApiKey.text.toString().trim())
                .putString("time_setting", timeKey)
                .putString("custom_datetime", editCustomDateTime.text.toString().trim())
                .putBoolean("auto_lock", switchAutoLock.isChecked)
                .putBoolean("dark_theme", switchDarkTheme.isChecked)
                .putBoolean("vibrate_on_complete", switchVibrate.isChecked)
                .apply()

            startActivity(Intent(this, BlackScreenActivity::class.java))
            finish()
        }
    }
}
