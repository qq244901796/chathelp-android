package com.jev.probe.jev

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StructuredCompletionTest {
    private fun request() = JSONObject().put("model", "glm-4-flash-250414")
        .put("temperature", 0.8).put("response_format", JSONObject().put("type", "json_object"))
        .put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", "必须输出三个回复"))
            .put(JSONObject().put("role", "user").put("content", "固定虚构对话")))
    private fun completion(content: String, reason: String = "stop") = JSONObject().put("choices", JSONArray()
        .put(JSONObject().put("finish_reason", reason).put("message", JSONObject().put("content", content))))
    private val valid = """{"replies":["好呀","我看下时间","我们商量一下"]}"""

    @Test fun oneRegenerationKeepsOriginalModelAndDataAndReturnsValidatedResult() {
        val body = request()
        val original = body.toString()
        val sent = mutableListOf<JSONObject>()
        val result = StructuredCompletion.request(body, Route.REPLY, post = {
            sent.add(it)
            if (sent.size == 1) completion("{\"replies\":[\"private model output\"]}") else completion(valid)
        }, parse = ModelJson::replies)
        assertEquals(3, result.size)
        assertEquals(2, sent.size)
        assertEquals(original, body.toString())
        assertEquals(sent[0].getString("model"), sent[1].getString("model"))
        assertEquals(sent[0].getJSONArray("messages").getJSONObject(1).toString(), sent[1].getJSONArray("messages").getJSONObject(1).toString())
        assertEquals(0.0, sent[1].getDouble("temperature"), 0.0)
        assertTrue(sent[1].toString().contains("上一轮输出未通过校验"))
        assertFalse(sent[1].toString().contains("private model output"))
    }

    @Test fun repeatedInvalidOutputStopsAfterOneCorrectionWithoutFakeReplies() {
        var calls = 0
        val error = assertThrows(ModelFormatException::class.java) {
            StructuredCompletion.request(request(), Route.REPLY, post = {
                calls++; completion("{\"replies\":[\"private invalid value\"]}")
            }, parse = ModelJson::replies)
        }
        assertEquals(2, calls)
        assertEquals(Route.REPLY, error.route)
        assertFalse(error.message!!.contains("private invalid value"))
    }

    @Test fun authFailuresAndContentFilteringAreNotFormatRetries() {
        for (filter in listOf(false, true)) {
            var calls = 0
            assertThrows(ApiException::class.java) {
                StructuredCompletion.request(request(), Route.REPLY, post = {
                    calls++
                    if (filter) completion("", "content_filter") else throw ApiException(Route.REPLY, 401, "无效密钥")
                }, parse = ModelJson::replies)
            }
            assertEquals(1, calls)
        }
    }

    @Test fun validFirstResponseNeedsNoRegeneration() {
        var calls = 0
        StructuredCompletion.request(request(), Route.REPLY, post = { calls++; completion(valid) }, parse = ModelJson::replies)
        assertEquals(1, calls)
    }
}
