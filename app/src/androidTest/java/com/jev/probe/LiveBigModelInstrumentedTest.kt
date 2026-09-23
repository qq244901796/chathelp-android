package com.jev.probe

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.jev.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/** Explicit opt-in only. Uses fixed fictional text and never exports credentials or responses. */
@RunWith(AndroidJUnit4::class)
class LiveBigModelInstrumentedTest {
    @Test fun fictionalDialogueWithConfiguredFreeModel() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val mode = InstrumentationRegistry.getArguments().getString("liveBigModel")
        assumeTrue("Live network test is opt-in", mode == "diagnose" || mode == "verify")
        val prefs = Prefs(inst.targetContext)
        assumeTrue("A BigModel key must already be configured on this device", prefs.hasKey())
        assumeTrue("Only the configured free BigModel route may be used",
            prefs.judgeProvider == Prefs.PROVIDER_BIGMODEL &&
                URI(prefs.judgeEndpoint()).host == "open.bigmodel.cn" &&
                prefs.judgeModel in setOf("glm-4-flash", "glm-4-flash-250414"))
        val sample = ChatSnapshot("虚构测试会话", listOf(
            Msg("me", "明天下午三点在图书馆见面，可以吗？"), Msg("other", "可以，到时候联系。")))
        if (mode == "diagnose") {
            val questions = JevQuestions.judge()
            val response = HttpJson.post(prefs.judgeEndpoint(), prefs.judgeKey,
                BigModelJudge.request(prefs.judgeModel, JevQuestions.buildState(sample, "普通朋友"), questions), Route.JUDGE)
            val content = ModelJson.content(response, Route.JUDGE)
            val obj = runCatching { JSONObject(content) }.getOrNull()
            val answers = obj?.optJSONObject("answers")
            val details = mutableListOf("root_object=${obj != null}; answers_object=${answers != null}")
            questions.keys().forEach { key ->
                val q = questions.getJSONObject(key)
                val a = answers?.optJSONObject(key)
                val parts = mutableListOf("$key: object=${a != null}")
                if (a != null) when (q.getString("type")) {
                    "noul" -> parts.add("noul_type=${a.opt("noul")?.javaClass?.simpleName}")
                    "score" -> parts.add("score_type=${a.opt("score")?.javaClass?.simpleName}; confidence_type=${a.opt("confidence")?.javaClass?.simpleName}")
                    "choice" -> {
                        val options = q.getJSONObject("criteria").keys().asSequence().toSet()
                        val probabilities = a.optJSONObject("probabilities")
                        parts.add("choice_valid=${a.optString("choice") in options}; confidence_type=${a.opt("confidence")?.javaClass?.simpleName}")
                        parts.add("probability_keys_match=${probabilities?.keys()?.asSequence()?.toSet() == options}")
                        if (probabilities != null) {
                            val numeric = options.mapNotNull { (probabilities.opt(it) as? Number)?.toDouble() }
                            parts.add("numeric_count=${numeric.size}; total=${numeric.sum()}")
                        }
                    }
                }
                details.add(parts.joinToString("; "))
            }
            inst.sendStatus(0, Bundle().apply { putString("stream", "\nSTRUCTURE_DIAG\n${details.joinToString("\n")}\n") })
            assertEquals(7, ModelJson.answers(content, questions).length())
        } else {
            assumeTrue("Reply must use the same free BigModel service", URI(prefs.replyEndpoint()).host == "open.bigmodel.cn" &&
                prefs.replyModel in setOf("glm-4-flash", "glm-4-flash-250414"))
            val scenarios = listOf(sample, ChatSnapshot("另一组虚构对话", listOf(
                Msg("me", "这周末一起去公园散步吧。"), Msg("other", "好呀，周六上午有空，你想几点出发？"))))
            scenarios.forEachIndexed { index, scenario ->
                val result = JevClient(prefs).analyze(scenario, "普通朋友")
                assertNull(result.error, result.error)
                assertNotNull(result.trueIntent)
                assertNotNull(result.dangerLevel)
                assertEquals(3, result.rankedReplies.size)
                assertEquals(1.0, result.rankedReplies.sumOf { it.prob }, 0.00001)
                inst.sendStatus(0, Bundle().apply { putString("stream", "\nLIVE_BIGMODEL scenario ${index + 1}: seven judgments, three replies, ranking passed.\n") })
            }
        }
    }
}
