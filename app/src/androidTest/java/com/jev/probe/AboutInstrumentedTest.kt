package com.jev.probe

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AboutInstrumentedTest {
    @Test fun releaseContainsReadableAttributionPrivacyAndLicenses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(target, AboutActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AboutActivity
        fun text(path: String) = target.assets.open(path).bufferedReader().use { it.readText() }
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else emptyList()
        fun waitFor(check: (List<View>) -> Boolean) {
            var done = false
            for (attempt in 0 until 60) {
                instrumentation.runOnMainSync { done = check(descendants(activity.window.decorView)) }
                if (done) break
                android.os.SystemClock.sleep(50)
            }
            assertTrue("Document should become readable without blocking the UI", done)
        }
        try {
            assertTrue(text("licenses/LICENSE").contains("Finderchangchang"))
            assertTrue(text("legal/ABOUT.md").contains("qq244901796/chathelp-android"))
            assertTrue(text("legal/PRIVACY.md").contains("统计"))
            assertTrue(text("legal/THIRD_PARTY_NOTICES.txt").contains("The JSR-330 Expert Group"))
            assertTrue(text("legal/THIRD_PARTY_NOTICES.txt").contains("Apache License"))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                val about = views.filterIsInstance<Button>().first { it.text == "关于与来源" }
                about.performClick()
                assertTrue(views.filterIsInstance<TextView>().any {
                    it.visibility == View.VISIBLE && it.text.contains("非官方修改版")
                })
            }
            waitFor { views -> views.filterIsInstance<TextView>().any { it.text.contains("qq244901796/chathelp-android") } }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(target.getExternalFilesDir(null), "about-test.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            instrumentation.runOnMainSync {
                descendants(activity.window.decorView).filterIsInstance<Button>()
                    .first { it.text == "第三方组件清单与完整许可" }.performClick()
            }
            waitFor { views -> views.filterIsInstance<Button>().any { it.text == "下一页" && it.isEnabled } }
            instrumentation.runOnMainSync {
                descendants(activity.window.decorView).filterIsInstance<Button>()
                    .first { it.text == "下一页" }.performClick()
            }
            waitFor { views -> views.filterIsInstance<TextView>().any { it.text.startsWith(" 2 / ") } }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
