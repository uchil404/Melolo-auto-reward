package com.melolo.helper.melolo
import android.view.accessibility.AccessibilityNodeInfo
import com.melolo.helper.NodeFinder

/** I. Ad close hanya jika tombol close/dismiss sah terdeteksi */
object AdCloseDetector{
    private val closeIds=listOf("close","dismiss","skip","btn_close","ad_close")
    fun findClose(root: AccessibilityNodeInfo?): AccessibilityNodeInfo?{
        if(root==null) return null
        return find(root)
    }
    private fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo?{
        val rid=(n.viewIdResourceName?:"").lowercase()
        val cd=(n.contentDescription?.toString()?:"").lowercase()
        val txt=(n.text?.toString()?:"").lowercase()
        val isAdOverlay = rid.contains("ad") || cd.contains("ad")
        // keyword hanya valid jika dalam ad overlay + confidence tinggi
        if(isAdOverlay && (closeIds.any{rid.contains(it)||cd.contains(it)||txt==it}) && n.isVisibleToUser && n.isClickable) return NodeFinder.resolveActionable(n) ?: n
        for(i in 0 until n.childCount){ n.getChild(i)?.let{ find(it)?.let{return it}} }
        return null
    }
}
object AdCloseController{
    fun clickIfSafe(root: AccessibilityNodeInfo?): Boolean{
        val node=AdCloseDetector.findClose(root) ?: return false
        // jangan klik install/download/CTA
        val t=(node.text?.toString()?:"").lowercase()
        if(listOf("install","download","buy","purchase").any{t.contains(it)}) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
