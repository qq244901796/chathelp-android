package com.jev.probe.jev

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelJsonTest {
    private fun validAnswers(questions: JSONObject): JSONObject {
        val answers = JSONObject()
        questions.keys().forEach { key ->
            val question = questions.getJSONObject(key)
            answers.put(key, when (question.getString("type")) {
                "noul" -> JSONObject().put("noul", 0.7)
                "score" -> JSONObject().put("score", 4.0).put("confidence", 0.6)
                else -> {
                    val keys = question.getJSONObject("criteria").keys().asSequence().toList()
                    JSONObject().put("choice", keys.first()).put("confidence", 0.7)
                        .put("probabilities", JSONObject().also { p ->
                            keys.forEach { p.put(it, 1.0 / keys.size) }
                        })
                }
            })
        }
        return JSONObject().put("answers", answers)
    }

    @Test fun allSevenAnswersAndZeroBasedRiskScale() {
        val questions = JevQuestions.judge()
        val answers = ModelJson.answers(validAnswers(questions).toString(), questions)
        assertEquals(7, answers.length())
        assertEquals(10, answers.getJSONObject("danger_level").getJSONObject("legend").length())
        assertEquals(4.0, answers.getJSONObject("danger_level").getDouble("score"), 0.0)
    }

    @Test fun rejectsMissingUnknownAndOutOfRangeAnswers() {
        val questions = JevQuestions.judge()
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.remove("true_intent") },
            { it.getJSONObject("true_intent").put("choice", "invented") },
            { it.getJSONObject("danger_level").put("score", 10) },
            { it.getJSONObject("should_reply_now").put("noul", -0.1) },
            { it.getJSONObject("tension_resolved").put("noul", "0.6") },
            { it.getJSONObject("true_intent").put("confidence", 1.1) },
            { it.getJSONObject("true_intent").getJSONObject("probabilities").put("unexpected", 0.1) },
            { it.getJSONObject("true_intent").put("probabilities", JSONObject()) }
        )
        mutations.forEach { mutate ->
            val response = validAnswers(questions)
            mutate(response.getJSONObject("answers"))
            assertThrows(ApiException::class.java) { ModelJson.answers(response.toString(), questions) }
        }
    }

    @Test fun rankingRequiresAllCandidatesAndNormalizesSmallRoundingError() {
        val questions = JevQuestions.rankQuestion(listOf("好呀", "我看看时间", "我们商量一下"))
        val response = validAnswers(questions)
        val answer = response.getJSONObject("answers").getJSONObject("best_reply")
        answer.put("choice", "reply_b").put("probabilities",
            JSONObject().put("reply_a", 0.2).put("reply_b", 0.5).put("reply_c", 0.29))
        val output = ModelJson.answers(response.toString(), questions).getJSONObject("best_reply")
        assertEquals(0.5 / 0.99, output.getJSONObject("probabilities").getDouble("reply_b"), 0.00001)
        answer.put("choice", "reply_a")
        assertEquals("reply_b", ModelJson.answers(response.toString(), questions)
            .getJSONObject("best_reply").getString("choice"))
    }

    @Test fun derivesOnlyUnambiguousChoiceFromCompleteValidatedProbabilities() {
        val questions = JevQuestions.judge()
        val response = validAnswers(questions)
        val answer = response.getJSONObject("answers").getJSONObject("true_intent")
        val probabilities = answer.getJSONObject("probabilities")
        probabilities.keys().asSequence().toList().forEach { probabilities.put(it, 0.0) }
        probabilities.put("casual_chat", 1.0)
        answer.put("choice", "friendly_logistics") // Reproduces GLM's invented label.
        val parsed = ModelJson.answers(response.toString(), questions).getJSONObject("true_intent")
        assertEquals("casual_chat", parsed.getString("choice"))
        assertEquals(probabilities.toString(), parsed.getJSONObject("probabilities").toString())
        answer.remove("choice")
        assertEquals("casual_chat", ModelJson.answers(response.toString(), questions)
            .getJSONObject("true_intent").getString("choice"))
        probabilities.put("casual_chat", 0.5).put("close_topic", 0.5)
        val error = assertThrows(ModelFormatException::class.java) { ModelJson.answers(response.toString(), questions) }
        assertTrue(error.message!!.contains("最高概率并列"))
        assertFalse(error.message!!.contains("friendly_logistics"))
        probabilities.put("casual_chat", 1.2).put("close_topic", -0.2)
        assertThrows(ModelFormatException::class.java) { ModelJson.answers(response.toString(), questions) }
    }

    @Test fun keepsModelTieBreakWhenValidAndNeverInventsMissingConfidence() {
        val questions = JevQuestions.rankQuestion(listOf("好呀", "我看看时间", "我们商量一下"))
        val response = validAnswers(questions)
        val answer = response.getJSONObject("answers").getJSONObject("best_reply")
        answer.put("choice", "reply_b")
        assertEquals("reply_b", ModelJson.answers(response.toString(), questions).getJSONObject("best_reply").getString("choice"))
        answer.remove("confidence")
        val error = assertThrows(ModelFormatException::class.java) { ModelJson.answers(response.toString(), questions, Route.RANK) }
        assertEquals(Route.RANK, error.route)
        assertTrue(error.message!!.contains("confidence"))
    }

    @Test fun acceptsStructuredRepliesAndCodeFence() {
        val replies = "{\"replies\":[\"好呀\",\"我看下时间\",\"我们商量一下\"]}"
        assertEquals(listOf("好呀", "我看下时间", "我们商量一下"), ModelJson.replies(replies))
        assertEquals(ModelJson.replies(replies), ModelJson.replies("```json\n${replies}\n```"))
        assertEquals(3, ModelJson.replies("[\"好呀\",\"我看看\",\"晚点聊\"]").size)
    }

    @Test fun rejectsFakeFallbackEmptyDuplicateAndMalformedReplies() {
        listOf("", "回复一\n回复二\n回复三", "[\"好\",\"好\",\"好\"]",
            "[\"好\",\"  \",\"再说\"]", "[\"好\",\"再说\"]", "[1,2,3]",
            "[\"${"长".repeat(41)}\",\"好\",\"再说\"]", "[\"好\",\"行\",\"嗯\"] extra"
        ).forEach { text -> assertThrows(ApiException::class.java) { ModelJson.replies(text) } }
    }

    @Test fun emptyAndTruncatedCompletionAreErrorsWithoutEchoingPayload() {
        assertThrows(ApiException::class.java) { ModelJson.content(JSONObject(), Route.REPLY) }
        val response = JSONObject("""{"choices":[{"finish_reason":"length","message":{"content":"private content"}}]}""")
        val error = assertThrows(ApiException::class.java) { ModelJson.content(response, Route.REPLY) }
        assertFalse(error.message!!.contains("private content"))
    }

    @Test fun bigModelUsesChatProtocolWithContextAndJsonOutput() {
        val state = JSONObject().put("background", "联系人的背景").put("history", "先前的约定")
        val body = BigModelJudge.request("glm-4-flash-250414", state, JevQuestions.judge())
        assertFalse(body.has("state"))
        assertFalse(body.has("questions"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals(state.toString(), body.getJSONArray("messages").getJSONObject(1).getString("content"))
        val system = body.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system.contains("true_intent"))
        assertTrue(system.contains("criteria 从 0 开始编号"))
        val example = system.substringAfter("完整 JSON 格式示例（示例数值仅说明格式，必须按实际对话重新判断）：").substringBefore('\n')
        assertEquals(7, ModelJson.answers(example, JevQuestions.judge()).length())
    }
}
