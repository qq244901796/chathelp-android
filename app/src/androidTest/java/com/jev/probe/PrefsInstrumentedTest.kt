package com.jev.probe

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jev.probe.core.Prefs
import com.jev.probe.jev.ApiException
import com.jev.probe.jev.VisionClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsInstrumentedTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "glm_test_prefs"
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            target.getSharedPreferences(fileName, mode)
    }
    private val store get() = context.getSharedPreferences(Prefs.PREFS_MAIN, Context.MODE_PRIVATE)

    @After fun cleanup() { target.deleteSharedPreferences(fileName) }

    @Test fun newInstallNeedsOnlyOneBigModelKeyAndKeepsOcrLocal() {
        store.edit().clear().commit()
        val prefs = Prefs(context)
        assertEquals(Prefs.PROVIDER_BIGMODEL, prefs.judgeProvider)
        assertEquals(Prefs.BIGMODEL_MODEL, prefs.judgeModel)
        assertEquals(Prefs.BIGMODEL_MODEL, prefs.replyModel)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", prefs.judgeEndpoint())
        assertEquals(prefs.judgeEndpoint(), prefs.replyEndpoint())
        prefs.judgeKey = "test-bigmodel-key"
        assertEquals(prefs.judgeKey, prefs.effectiveReplyKey())
        assertFalse(prefs.visionEnabled)
        assertEquals("", prefs.effectiveVisionKey())
        assertTrue(prefs.ocrFallback)
        assertEquals(Prefs.OCR_MLKIT, prefs.ocrEngine)
        assertFalse(prefs.contextEnabled)
    }

    @Test fun legacyImplicitDefaultsAndUserSettingsSurviveUpgrade() {
        store.edit().clear().putString("openrouter_key", "test-legacy-key")
            .putString("relationship", "朋友").putBoolean("ocr_fallback", false).commit()
        val prefs = Prefs(context)
        assertEquals(Prefs.PROVIDER_OPENROUTER, prefs.judgeProvider)
        assertEquals(Prefs.DEFAULT_JUDGE_BASE_OPENROUTER, prefs.judgeBaseUrl)
        assertEquals(Prefs.OPENROUTER_REPLY_BASE, prefs.replyBaseUrl)
        assertEquals(Prefs.OPENROUTER_REPLY_MODEL, prefs.replyModel)
        assertEquals("test-legacy-key", prefs.judgeKey)
        assertEquals("朋友", prefs.relationship)
        assertFalse(prefs.ocrFallback)
        prefs.judgeKey = ""
        assertEquals("", Prefs(context).judgeKey)
    }

    @Test fun customExistingConfigRemainsAndExplicitPresetClearsForeignKeys() {
        store.edit().clear().putBoolean("prefs_migrated_v13", true)
            .putString("judge_provider", "custom").putString("judge_base_url", "https://example.com/judge")
            .putString("judge_model", "original").putString("judge_key", "test-old-key")
            .putString("reply_key", "test-other-key").putString("vision_key", "test-vision-key")
            .putString("relationship", "同事").commit()
        val prefs = Prefs(context)
        assertEquals("https://example.com/judge", prefs.judgeEndpoint())
        assertEquals("original", prefs.judgeModel)
        prefs.applyBigModelPreset()
        val reloaded = Prefs(context)
        assertEquals(Prefs.BIGMODEL_MODEL, reloaded.judgeModel)
        assertEquals(Prefs.BIGMODEL_BASE, reloaded.replyBaseUrl)
        assertEquals("", reloaded.judgeKey)
        assertEquals("", reloaded.replyKey)
        assertEquals("", reloaded.visionKey)
        assertEquals("同事", reloaded.relationship)
        assertFalse(reloaded.visionEnabled)
    }

    @Test fun credentialsAreNeverInheritedAcrossProviders() {
        store.edit().clear().commit()
        val prefs = Prefs(context)
        prefs.judgeKey = "test-bigmodel-key"
        prefs.replyBaseUrl = Prefs.DEEPSEEK_BASE
        assertEquals("", prefs.effectiveReplyKey())
        prefs.replyKey = "test-deepseek-key"
        assertEquals("test-deepseek-key", prefs.effectiveReplyKey())
        assertEquals("", prefs.effectiveVisionKey())
        prefs.replyBaseUrl = Prefs.BIGMODEL_BASE + "/chat/completions/"
        assertEquals(Prefs.chatEndpoint(Prefs.BIGMODEL_BASE), prefs.replyEndpoint())
    }

    @Test fun disabledVisionFailsBeforeAnyNetworkOrImageDecode() {
        store.edit().clear().commit()
        val error = assertThrows(ApiException::class.java) {
            VisionClient(Prefs(context)).ask("not-an-image", "测试")
        }
        assertTrue(error.message!!.contains("未启用"))
    }
}
