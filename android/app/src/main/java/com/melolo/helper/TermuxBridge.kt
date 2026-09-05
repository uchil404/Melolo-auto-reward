package com.melolo.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bridges communication between Termux (Python controller) and the Android APK.
 *
 * Uses multiple mechanisms (no root required):
 *   - BroadcastReceiver for incoming commands from Termux (via `am broadcast`)
 *   - SharedPreferences for persistent status
 *   - Intent-based communication back to Termux
 *
 * Termux → APK:  `am broadcast -a com.melolo.helper.COMMAND --es command "start"`
 * APK → Termux: stored in SharedPreferences; Termux reads via `content query`
 */
object TermuxBridge {

    const val COMMAND_ACTION = "com.melolo.helper.COMMAND"
    const val STATUS_ACTION = "com.melolo.helper.STATUS"
    const val PREFS_NAME = "melolo_helper_status"

    const val EXTRA_COMMAND = "command"
    const val EXTRA_STATE = "state"
    const val EXTRA_CLAIMS = "claims"
    const val EXTRA_LAST_CLAIM = "last_claim"
    const val EXTRA_LAST_ERROR = "last_error"
    const val EXTRA_SERVICE_RUNNING = "service_running"
    const val EXTRA_AUTOMATION_ENABLED = "automation_enabled"

    enum class Command {
        START,
        STOP,
        INSPECT,
        EMERGENCY_STOP,
        STATUS,
        TEST,
        CONFIG,
        UNKNOWN;

        companion object {
            fun fromString(s: String): Command = try {
                valueOf(s.uppercase())
            } catch (_: Exception) {
                UNKNOWN
            }
        }
    }

    private var commandReceiver: CommandReceiver? = null
    private var commandListener: ((Command, Map<String, String>) -> Unit)? = null

    /**
     * Register to receive commands from Termux via broadcast.
     */
    fun registerReceiver(context: Context, listener: (Command, Map<String, String>) -> Unit) {
        commandListener = listener
        val receiver = CommandReceiver(listener)
        commandReceiver = receiver

        val filter = IntentFilter(COMMAND_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context, receiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter)
        }
        Logger.info("TermuxBridge: receiver registered")
    }

    fun unregisterReceiver(context: Context) {
        commandReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
                // already unregistered
            }
        }
        commandReceiver = null
        commandListener = null
        Logger.info("TermuxBridge: receiver unregistered")
    }

    /**
     * Write status to SharedPreferences so Termux can read it.
     */
    fun updateStatus(
        context: Context,
        state: String,
        claims: Int,
        lastClaim: String,
        lastError: String,
        serviceRunning: Boolean,
        automationEnabled: Boolean
    ) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(EXTRA_STATE, state)
            .putInt(EXTRA_CLAIMS, claims)
            .putString(EXTRA_LAST_CLAIM, lastClaim)
            .putString(EXTRA_LAST_ERROR, lastError)
            .putBoolean(EXTRA_SERVICE_RUNNING, serviceRunning)
            .putBoolean(EXTRA_AUTOMATION_ENABLED, automationEnabled)
            .apply()
    }

    /**
     * Read status from SharedPreferences.
     */
    fun readStatus(context: Context): Map<String, String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return mapOf(
            EXTRA_STATE to (prefs.getString(EXTRA_STATE, "IDLE") ?: "IDLE"),
            EXTRA_CLAIMS to prefs.getInt(EXTRA_CLAIMS, 0).toString(),
            EXTRA_LAST_CLAIM to (prefs.getString(EXTRA_LAST_CLAIM, "NONE") ?: "NONE"),
            EXTRA_LAST_ERROR to (prefs.getString(EXTRA_LAST_ERROR, "NONE") ?: "NONE"),
            EXTRA_SERVICE_RUNNING to prefs.getBoolean(EXTRA_SERVICE_RUNNING, false).toString(),
            EXTRA_AUTOMATION_ENABLED to prefs.getBoolean(EXTRA_AUTOMATION_ENABLED, false).toString()
        )
    }

    /**
     * Send a broadcast status update (for apps that listen).
     */
    fun sendStatusBroadcast(
        context: Context,
        state: String,
        claims: Int,
        lastClaim: String,
        lastError: String
    ) {
        val intent = Intent(STATUS_ACTION).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_CLAIMS, claims)
            putExtra(EXTRA_LAST_CLAIM, lastClaim)
            putExtra(EXTRA_LAST_ERROR, lastError)
        }
        context.sendBroadcast(intent)
        Logger.debug("TermuxBridge: status broadcast sent: $state")
    }

    private class CommandReceiver(
        private val listener: (Command, Map<String, String>) -> Unit
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == COMMAND_ACTION) {
                val commandStr = intent.getStringExtra(EXTRA_COMMAND) ?: "UNKNOWN"
                val command = Command.fromString(commandStr)

                val extras = mutableMapOf<String, String>()
                intent.extras?.keySet()?.forEach { key ->
                    if (key != EXTRA_COMMAND) {
                        extras[key] = intent.extras?.get(key)?.toString() ?: ""
                    }
                }

                Logger.info("TermuxBridge: received command: $commandStr")
                listener(command, extras)
            }
        }
    }
}