package com.melolo.helper.melolo
import android.view.accessibility.AccessibilityNodeInfo

object AdCloseVerifier{
    enum class Result{ AD_CLOSED, UNKNOWN }
    fun verify(before: String, after: String): Result =
        if(before.contains("ad",true) && !after.contains("ad",true)) Result.AD_CLOSED else Result.UNKNOWN
}
object AccountManager{
    data class Account(val id:String, val name:String, var status:String="READY")
}
