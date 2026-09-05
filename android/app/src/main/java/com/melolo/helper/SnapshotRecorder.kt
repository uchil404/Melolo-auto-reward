package com.melolo.helper

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SnapshotRecorder — dump hierarchy UI ke JSON yang kompatibel dengan
 * termux/snapshot.py (inspect --json / inspect --diff / test --snapshot).
 *
 * Format:
 * {
 *   "package": "...", "activity": "...", "timestamp": "...",
 *   "nodes": [ { class, text, resource_id, content_desc,
 *                clickable, enabled, visible, bounds } ]
 * }
 */
object SnapshotRecorder {

    fun record(
        root: AccessibilityNodeInfo?,
        packageName: String = "",
        activity: String = ""
    ): JSONObject {
        val nodes = JSONArray()
        collect(root, 0, nodes)
        return JSONObject()
            .put("package", packageName.ifEmpty { root?.packageName?.toString() ?: "" })
            .put("activity", activity)
            .put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("nodes", nodes)
    }

    private fun collect(node: AccessibilityNodeInfo?, depth: Int, out: JSONArray) {
        if (node == null || depth > 30) return
        val b = Rect().also {
            try { node.getBoundsInScreen(it) } catch (e: Exception) { /* abaikan */ }
        }
        out.put(
            JSONObject()
                .put("class", (node.className?.toString() ?: "").substringAfterLast("."))
                .put("text", node.text?.toString() ?: "")
                .put("resource_id", node.viewIdResourceName ?: "")
                .put("content_desc", node.contentDescription?.toString() ?: "")
                .put("clickable", node.isClickable)
                .put("enabled", node.isEnabled)
                .put("visible", node.isVisibleToUser)
                .put("bounds", b.toShortString())
        )
        for (i in 0 until node.childCount) {
            try {
                collect(node.getChild(i), depth + 1, out)
            } catch (e: Exception) { /* node recycled */ }
        }
    }
}
