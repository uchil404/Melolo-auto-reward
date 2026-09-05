package com.melolo.helper

/**
 * Centralized log sink for the Melolo Reward Helper APK.
 * All modules write through this object so logs are consistent
 * and can be forwarded to Termux via TermuxBridge.
 */
object Logger {
    private const val TAG = "MeloloHelper"
    private val listeners = mutableListOf<(String, String) -> Unit>()

    enum class Level(val tag: String) {
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR"),
        DEBUG("DEBUG"),
        STATE("STATE"),
        CLAIM("CLAIM"),
        SAFETY("SAFETY")
    }

    fun addListener(listener: (String, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, String) -> Unit) {
        listeners.remove(listener)
    }

    fun info(message: String) = log(Level.INFO, message)
    fun warn(message: String) = log(Level.WARN, message)
    fun error(message: String) = log(Level.ERROR, message)
    fun debug(message: String) = log(Level.DEBUG, message)
    fun state(message: String) = log(Level.STATE, message)
    fun claim(message: String) = log(Level.CLAIM, message)
    fun safety(message: String) = log(Level.SAFETY, message)

    private fun log(level: Level, message: String) {
        val formatted = "${level.tag} | $message"
        android.util.Log.d(TAG, formatted)
        for (listener in listeners) {
            try {
                listener(level.tag, message)
            } catch (_: Exception) {
                // best-effort; don't let a broken listener crash the service
            }
        }
    }
}