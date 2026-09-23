package com.jev.probe

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jev.probe.capture.ChatCaptureService
import com.jev.probe.capture.TestChatAdapter
import com.jev.probe.capture.ocr.MlKitOcr
import com.jev.probe.core.AnalysisSession
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Install test-chat first. All UI inspected belongs to the offline fixture app. */
@RunWith(AndroidJUnit4::class)
class TestChatInstrumentedTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext
    private val automation get() = inst.uiAutomation
    private val pkg = TestChatAdapter.PACKAGE
    private val adapter = TestChatAdapter()
    private val isolated = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            target.getSharedPreferences("fixture_test_prefs", mode)
    }
    private var service: Harness? = null

    private inner class Harness : ChatCaptureService() {
        init { attachBaseContext(isolated) }
        override fun getRootInActiveWindow(): AccessibilityNodeInfo? = automation.rootInActiveWindow
    }

    @Before fun launch() {
        target.deleteSharedPreferences("fixture_test_prefs")
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        target.startActivity(Intent().setComponent(ComponentName(pkg, "$pkg.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitFor { root()?.findAccessibilityNodeInfosByViewId("$pkg:id/chat_title")?.isNotEmpty() == true }
        waitFor { snapshot()?.messages?.size == 2 }
    }

    @After fun cleanup() {
        service?.let { inst.runOnMainSync { it.onDestroy() } }
        target.deleteSharedPreferences("fixture_test_prefs")
    }

    private fun root() = automation.rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
    private fun snapshot() = root()?.let { adapter.extract(it, target.resources) }
    private fun node(id: String): AccessibilityNodeInfo = requireNotNull(root())
        .findAccessibilityNodeInfosByViewId("$pkg:id/$id").first { it.isVisibleToUser }
    private fun click(id: String) {
        assertTrue(node(id).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(400)
    }
    private fun type(text: String) {
        assertTrue(node("chat_input").performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }))
        SystemClock.sleep(300)
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(100)
        }
        fail("Fixture UI did not reach the expected state")
    }
    private fun field(name: String) = ChatCaptureService::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun call(name: String) = inst.runOnMainSync {
        ChatCaptureService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
    }
    private fun harness(auto: Boolean): Prefs {
        val prefs = Prefs(isolated).apply { enabled = true; autoAnalyze = auto; judgeKey = "" }
        inst.runOnMainSync {
            service = Harness()
            field("prefs").set(service, prefs)
        }
        return prefs
    }

    @Test fun textCaptureExcludesDraftsAndControlsAndPreservesSides() {
        val before = requireNotNull(snapshot())
        assertEquals("测试好友小明", before.title)
        assertEquals(listOf("me", "other"), before.messages.map { it.side })
        assertTrue(before.messages.first().text.contains("图书馆"))
        type("尚未发送的草稿")
        assertEquals(before.signature(), snapshot()!!.signature())
        click("send_me")
        waitFor { snapshot()?.messages?.lastOrNull()?.text == "尚未发送的草稿" }
        assertEquals("me", snapshot()!!.latestFrom)
        click("receive_other")
        waitFor { snapshot()?.messages?.size == 4 }
        assertEquals("other", snapshot()!!.latestFrom)
    }

    @Test fun canvasModeHasNoReadableBodyAndScreenshotOcrReadsBothBubbles() {
        click("ocr_mode")
        waitFor { snapshot()?.bubbleRects?.size == 2 }
        val captured = requireNotNull(snapshot())
        assertTrue(captured.messages.isEmpty())
        assertTrue(captured.ocrFallbackAllowed)
        assertEquals(listOf("me", "other"), captured.bubbleRects.map { it.side })
        assertNull(node("message_me").text)
        assertNull(node("message_other").text)
        val screen = requireNotNull(automation.takeScreenshot())
        // UiAutomation returns a full-display bitmap; OEM logical/physical sizes can differ.
        val display = target.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
        val sx = screen.width.toFloat() / display.width()
        val sy = screen.height.toFloat() / display.height()
        try {
            val recognized = captured.bubbleRects.map { bubble ->
                val bounds = bubble.rect
                val region = Rect((bounds.left * sx).toInt(), (bounds.top * sy).toInt(),
                    (bounds.right * sx).toInt(), (bounds.bottom * sy).toInt())
                val latch = CountDownLatch(1)
                var text = ""
                MlKitOcr().recognize(screen, region) { lines -> text = lines.joinToString("") { it.text }; latch.countDown() }
                assertTrue(latch.await(40, TimeUnit.SECONDS))
                text
            }
            assertTrue("First bubble OCR mismatch", recognized[0].contains("图书馆"))
            assertTrue("Second bubble OCR mismatch", recognized[1].contains("到时候联系"))
        } finally { screen.recycle() }
    }

    @Test fun modeResetAndChatSwitchInvalidateOldIdentityAndEmptyPageDoesNotOcrChrome() {
        val original = requireNotNull(snapshot())
        val session = AnalysisSession()
        session.select(pkg, original.title, original.signature())
        val token = session.begin()
        click("reset_chat")
        val reset = requireNotNull(snapshot())
        assertEquals(original.messages, reset.messages)
        assertNotEquals(original.signature(), reset.signature())
        session.select(pkg, reset.title, reset.signature())
        assertFalse(session.isCurrent(token))
        click("ocr_mode")
        assertNotEquals(reset.sourceRevision, snapshot()!!.sourceRevision)
        click("switch_chat")
        assertEquals("测试好友小红", snapshot()!!.title)
        click("clear_chat")
        val empty = requireNotNull(snapshot())
        assertTrue(empty.messages.isEmpty())
        assertTrue(empty.bubbleRects.isEmpty())
        assertFalse(empty.ocrFallbackAllowed)
    }

    @Test fun productionCaptureQueuesOnlyIncomingAndManualWorksWithAutoOff() {
        val prefs = harness(auto = true)
        call("maybeCapture")
        assertNotNull(field("pendingSnapshot").get(service))
        type("我的新消息")
        click("send_me")
        call("maybeCapture")
        assertNull(field("pendingSnapshot").get(service))
        type("编辑草稿")
        call("maybeCapture")
        assertNull(field("pendingSnapshot").get(service))
        prefs.autoAnalyze = false
        click("receive_other")
        call("maybeCapture")
        assertNull(field("pendingSnapshot").get(service))
        call("analyzeManual")
        assertNotNull(field("pendingSnapshot").get(service))
        // The blank key blocks networking; queued data still proves the trigger decision.
    }

    @Test fun productionFillOnlyChangesInputAndRejectsOldSession() {
        harness(auto = false)
        call("maybeCapture")
        val session = field("session").get(service) as AnalysisSession
        val token = session.token
        val before = requireNotNull(snapshot())
        val fill = ChatCaptureService::class.java.getDeclaredMethod("fillInput", String::class.java,
            java.lang.Long.TYPE, String::class.java, String::class.java).apply { isAccessible = true }
        fill.invoke(service, "这是候选回复，不会自动发送", token, pkg, before.title)
        waitFor { node("chat_input").text?.toString() == "这是候选回复，不会自动发送" }
        assertEquals(before.messages, snapshot()!!.messages)
        click("switch_chat")
        assertEquals("测试好友小红", snapshot()!!.title)
        // Reject by re-reading the target even before a service event invalidates the token.
        fill.invoke(service, "不应填入另一个会话", token, pkg, before.title)
        SystemClock.sleep(600)
        val after = node("chat_input")
        assertTrue("Old reply was inserted into another chat", after.isShowingHintText || after.text.isNullOrEmpty())
        click("switch_chat")
        assertEquals(before.title, snapshot()!!.title)
        fill.invoke(service, "旧回复不应在返回原会话后恢复有效", token, pkg, before.title)
        SystemClock.sleep(600)
        val returned = node("chat_input")
        assertTrue("Old reply survived A -> B -> A", returned.isShowingHintText || returned.text.isNullOrEmpty())
    }
}
