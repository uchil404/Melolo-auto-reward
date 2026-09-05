package com.melolo.helper
import android.view.accessibility.AccessibilityNodeInfo

/** P0: pisah CHECK_IN dari generic reward + TODAY/TOMORROW + CLAIM semantic */
enum class TaskKind { CHECK_IN_TASK, CLAIM_REWARD, WATCH_REWARD, CHEST_REWARD, AD_REWARD, UNKNOWN }

object TaskClassifier {
    private val checkInKw = listOf("check in","check-in","hadiah harian","daily","absen")
    private val claimKw = listOf("claim","klaim","collect","ambil")
    private val watchKw = listOf("watch","tonton","video","ad","iklan")
    private val claimedKw = listOf("claimed","diklaim","collected","sudah diterima","done")
    data class Res(val kind: TaskKind, val already: Boolean, val amount: String?)
    private val amtRe = Regex("""(\d+)\s*(coin|koin|point)?""", RegexOption.IGNORE_CASE)
    fun classify(n: AccessibilityNodeInfo): Res {
        val t = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()
        val already = claimedKw.any { t.contains(it) }
        val kind = when {
            checkInKw.any { t.contains(it) } -> TaskKind.CHECK_IN_TASK
            watchKw.any { t.contains(it) } -> TaskKind.WATCH_REWARD
            claimKw.any { t.contains(it) } -> TaskKind.CLAIM_REWARD
            else -> TaskKind.UNKNOWN
        }
        return Res(kind, already, amtRe.find(t)?.groupValues?.get(1))
    }
}
