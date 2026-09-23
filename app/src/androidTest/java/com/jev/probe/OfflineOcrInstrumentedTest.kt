package com.jev.probe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jev.probe.capture.ocr.MlKitOcr
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OfflineOcrInstrumentedTest {
    @Test fun bundledRecognizerReadsChineseWithoutApiKey() {
        // A deterministic synthetic chat image; no real contacts or messages are used.
        val bitmap = Bitmap.createBitmap(1080, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        canvas.drawText("你好，明天下午三点见面", 40f, 150f, paint)
        canvas.drawText("好的，我们到时联系", 40f, 300f, paint)
        val done = CountDownLatch(1)
        var result = ""
        MlKitOcr().recognize(bitmap, null) { lines ->
            result = lines.joinToString("") { it.text }
            done.countDown()
        }
        try {
            assertTrue("OCR did not finish", done.await(40, TimeUnit.SECONDS))
            assertTrue("Expected Chinese words were not recognized", result.contains("明天下午三点"))
            assertTrue(result.contains("到时联系"))
        } finally { bitmap.recycle() }
    }
}
