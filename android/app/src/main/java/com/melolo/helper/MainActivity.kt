package com.melolo.helper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusAccessibility: TextView
    private lateinit var statusMelolo: TextView
    private lateinit var statusAutomation: TextView
    private lateinit var textState: TextView
    private lateinit var textClaims: TextView
    private lateinit var textLastClaim: TextView
    private lateinit var textLastError: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnInspect: Button
    private lateinit var btnEmergencyStop: Button
    private lateinit var textHierarchy: TextView
    private lateinit var scrollHierarchy: ScrollView

    private val refreshInterval = 2000L
    private var isRefreshing = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            if (isRefreshing) {
                handler.postDelayed(this, refreshInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        refreshStatus()
        startRefreshing()
    }

    override fun onResume() {
        super.onResume()
        startRefreshing()
    }

    override fun onPause() {
        super.onPause()
        stopRefreshing()
    }

    private fun createLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Melolo Reward Helper"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        })

        // Status section
        statusAccessibility = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }
        statusMelolo = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }
        statusAutomation = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }

        root.addView(statusAccessibility)
        root.addView(statusMelolo)
        root.addView(statusAutomation)

        // Spacer
        root.addView(TextView(this).apply { text = ""; setPadding(0, 0, 0, 16) })

        // State
        textState = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }
        textClaims = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }
        textLastClaim = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }
        textLastError = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 8) }

        root.addView(textState)
        root.addView(textClaims)
        root.addView(textLastClaim)
        root.addView(textLastError)

        // Spacer
        root.addView(TextView(this).apply { text = ""; setPadding(0, 0, 0, 16) })

        // Buttons
        btnStart = Button(this).apply {
            text = "START"
            setOnClickListener { onStartClicked() }
        }
        btnStop = Button(this).apply {
            text = "STOP"
            setOnClickListener { onStopClicked() }
        }
        btnInspect = Button(this).apply {
            text = "INSPECT UI"
            setOnClickListener { onInspectClicked() }
        }
        btnEmergencyStop = Button(this).apply {
            text = "EMERGENCY STOP"
            setBackgroundColor(0xFFFF4444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { onEmergencyStopClicked() }
        }

        root.addView(btnStart)
        root.addView(btnStop)
        root.addView(btnInspect)
        root.addView(btnEmergencyStop)

        // Spacer
        root.addView(TextView(this).apply { text = ""; setPadding(0, 0, 0, 16) })

        // Hierarchy dump section
        scrollHierarchy = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        textHierarchy = TextView(this).apply {
            textSize = 11f
            setPadding(8, 8, 8, 8)
            text = "Hierarchy dump will appear here when INSPECT UI is active"
        }
        scrollHierarchy.addView(textHierarchy)
        root.addView(scrollHierarchy)

        return root
    }

    private fun onStartClicked() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val targetPackage = getTargetPackage()
        if (targetPackage.isBlank()) {
            Toast.makeText(this, "No target package configured", Toast.LENGTH_LONG).show()
            return
        }

        sendCommandToService(TermuxBridge.Command.START)
        Toast.makeText(this, "Automation started", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun onStopClicked() {
        sendCommandToService(TermuxBridge.Command.STOP)
        Toast.makeText(this, "Automation stopped", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun onInspectClicked() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        sendCommandToService(TermuxBridge.Command.INSPECT)
        Toast.makeText(this, "Inspect mode active — check hierarchy below", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ loadHierarchyDump() }, 1000)
    }

    private fun onEmergencyStopClicked() {
        sendCommandToService(TermuxBridge.Command.EMERGENCY_STOP)
        Toast.makeText(this, "EMERGENCY STOP — All automation halted", Toast.LENGTH_LONG).show()
        refreshStatus()
    }

    private fun sendCommandToService(command: TermuxBridge.Command) {
        val intent = Intent(TermuxBridge.COMMAND_ACTION).apply {
            setPackage(packageName)
            putExtra(TermuxBridge.EXTRA_COMMAND, command.name)
        }
        sendBroadcast(intent)
    }

    private fun refreshStatus() {
        val isAccessibilityOn = isAccessibilityEnabled()
        statusAccessibility.text = if (isAccessibilityOn) {
            "Accessibility: ● ONLINE"
        } else {
            "Accessibility: ● OFFLINE"
        }

        val status = TermuxBridge.readStatus(this)
        val currentPackage = getCurrentMeloloPackage()
        statusMelolo.text = if (currentPackage.isNotBlank()) {
            "Melolo: ● DETECTED ($currentPackage)"
        } else {
            "Melolo: ● NOT DETECTED"
        }

        val automationRunning = status[TermuxBridge.EXTRA_AUTOMATION_ENABLED] == "true"
        statusAutomation.text = if (automationRunning) {
            "Automation: ● RUNNING"
        } else {
            "Automation: ● STOPPED"
        }

        textState.text = "State: ${status[TermuxBridge.EXTRA_STATE] ?: "IDLE"}"
        textClaims.text = "Claims: ${status[TermuxBridge.EXTRA_CLAIMS] ?: "0"}"
        textLastClaim.text = "Last Claim: ${status[TermuxBridge.EXTRA_LAST_CLAIM] ?: "NONE"}"
        textLastError.text = "Last Error: ${status[TermuxBridge.EXTRA_LAST_ERROR] ?: "NONE"}"
    }

    private fun loadHierarchyDump() {
        // Hierarchy is sent via broadcast; in a real implementation,
        // we could also read it from SharedPreferences or a file.
        // For now, the INSPECT command triggers the service to dump.
        textHierarchy.text = "Inspect mode active.\nCheck logcat: adb logcat -s MeloloHelper:D\n\n"
        textHierarchy.append("Or use: melolo-helper inspect\n")
        textHierarchy.append("from Termux to see the full hierarchy dump.")
    }

    private fun startRefreshing() {
        if (!isRefreshing) {
            isRefreshing = true
            handler.post(refreshRunnable)
        }
    }

    private fun stopRefreshing() {
        isRefreshing = false
        handler.removeCallbacks(refreshRunnable)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun getTargetPackage(): String {
        val prefs = getSharedPreferences("melolo_helper_config", MODE_PRIVATE)
        return prefs.getString("target_package", "") ?: ""
    }

    private fun getCurrentMeloloPackage(): String {
        // In a real implementation, this would query the accessibility service
        // for the current foreground package. For now, return the configured target.
        return getTargetPackage()
    }
}