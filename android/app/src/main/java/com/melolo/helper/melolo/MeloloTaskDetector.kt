package com.melolo.helper.melolo
import android.view.accessibility.AccessibilityNodeInfo
import com.melolo.helper.*

object MeloloTaskDetector {
    data class Candidate(val node: AccessibilityNodeInfo, val type: MeloloTaskType, val score: Int)
    fun detect(root: AccessibilityNodeInfo?): List<Candidate> {
        if(root==null) return emptyList()
        val out=mutableListOf<Candidate>()
        scan(root, out)
        return out.sortedByDescending{it.score}
    }
    private fun scan(n: AccessibilityNodeInfo, out: MutableList<Candidate>){
        val rid=n.viewIdResourceName ?: ""
        val t=MeloloResourceMap.toTaskType(rid)
        if(t!=MeloloTaskType.UNKNOWN){
            val actionable=NodeFinder.resolveActionable(n) ?: n
            if(actionable.isVisibleToUser && actionable.isEnabled) out.add(Candidate(actionable,t,100))
        }
        for(i in 0 until n.childCount) n.getChild(i)?.let{scan(it,out)}
    }
}
