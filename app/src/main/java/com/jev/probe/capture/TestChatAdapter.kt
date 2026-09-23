package com.jev.probe.capture

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.core.BubbleRect
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg

/** Explicitly scoped fixture adapter; never impersonates a real messenger. */
class TestChatAdapter : ChatAppAdapter {
    override val pkg = PACKAGE

    override fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot? {
        if (root.packageName?.toString() != pkg) return null
        fun node(id: String) = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            .firstOrNull { it.isVisibleToUser }
        val title = node("chat_title")?.text?.toString() ?: return null
        val state = node("chat_state") ?: return null
        val area = node("chat_viewport") ?: return null
        if (node("chat_input") == null) return null
        val areaBounds = Rect().also { area.getBoundsInScreen(it) }
        val revision = state.contentDescription?.toString()
        val ocrMode = node("ocr_mode")?.isChecked == true
        val bubbles = ArrayList<Pair<BubbleRect, String?>>()
        for ((id, side) in listOf("message_me" to "me", "message_other" to "other")) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").forEach { bubble ->
                val bounds = Rect().also { bubble.getBoundsInScreen(it) }
                if (bubble.isVisibleToUser && bounds.intersect(areaBounds) && !bounds.isEmpty) {
                    bubbles.add(BubbleRect(bounds, side) to bubble.text?.toString())
                }
            }
        }
        bubbles.sortBy { it.first.rect.top }
        return ChatSnapshot(
            title = title,
            messages = if (ocrMode) emptyList() else bubbles.mapNotNull { (rect, text) ->
                text?.takeIf { it.isNotBlank() }?.let { Msg(rect.side, it) }
            },
            bubbleRects = if (ocrMode) bubbles.map { it.first } else emptyList(),
            sourceRevision = revision,
            ocrFallbackAllowed = ocrMode && bubbles.isNotEmpty()
        )
    }

    companion object { const val PACKAGE = "io.github.qq244901796.chathelp.testchat" }
}
