package com.jev.probe.jev

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs

/** Strict boundaries for untrusted model output. Errors never include chat text. */
internal object ModelJson {
    fun content(response: JSONObject, route: String): String {
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
            ?: fail(route, "未返回模型回答")
        val reason = choice.optString("finish_reason")
        if (reason == "length") fail(route, "模型回答被截断，请重试")
        if (reason == "content_filter" || reason == "sensitive") fail(route, "模型未能回答此内容")
        val content = choice.optJSONObject("message")?.opt("content") as? String
        return content?.trim()?.takeIf { it.isNotEmpty() }
            ?: fail(route, "模型返回空回答，请重试")
    }

    fun replies(content: String): List<String> = checked(Route.REPLY) {
        val value = decode(content)
        val array = when (value) {
            is JSONObject -> value.getJSONArray("replies")
            is JSONArray -> value // Existing compatible providers may still return an array.
            else -> error("shape")
        }
        require(array.length() == 3)
        val replies = (0..2).map {
            val text = (array.get(it) as? String)?.trim() ?: error("type")
            require(text.isNotEmpty() && text.codePointCount(0, text.length) <= 40)
            text
        }
        require(replies.distinct().size == 3)
        replies
    }

    /** Validate against the existing Jev question definitions, including every enum. */
    fun answers(content: String, questions: JSONObject): JSONObject = checked(Route.JUDGE) {
        val answers = (decode(content) as JSONObject).getJSONObject("answers")
        val expected = questions.keys().asSequence().toSet()
        require(answers.keys().asSequence().toSet() == expected)
        expected.forEach { key ->
            val question = questions.getJSONObject(key)
            val answer = answers.getJSONObject(key)
            when (question.getString("type")) {
                "noul" -> number(answer, "noul", 0.0, 1.0)
                "score" -> {
                    val levels = question.getJSONArray("criteria")
                    number(answer, "score", 0.0, (levels.length() - 1).toDouble())
                    number(answer, "confidence", 0.0, 1.0)
                    // Jev uses zero-based levels (0..9); do not trust a generated legend.
                    answer.put("legend", JSONObject().also { legend ->
                        for (i in 0 until levels.length()) legend.put(i.toString(), levels.getString(i))
                    })
                }
                "choice" -> {
                    val options = question.getJSONObject("criteria").keys().asSequence().toSet()
                    val selected = answer.get("choice") as? String ?: error("choice")
                    require(selected in options)
                    number(answer, "confidence", 0.0, 1.0)
                    val probabilities = answer.getJSONObject("probabilities")
                    require(probabilities.keys().asSequence().toSet() == options)
                    val values = options.associateWith { number(probabilities, it, 0.0, 1.0) }
                    val total = values.values.sum()
                    require(total > 0 && abs(total - 1.0) <= 0.05)
                    require(values.getValue(selected) >= values.values.max())
                    values.forEach { (option, probability) -> probabilities.put(option, probability / total) }
                }
                else -> error("question type")
            }
        }
        answers
    }

    private fun number(o: JSONObject, key: String, min: Double, max: Double): Double {
        val value = (o.get(key) as? Number)?.toDouble() ?: error("number")
        require(value.isFinite() && value in min..max)
        return value
    }

    private fun decode(content: String): Any {
        var json = content.trim()
        if (json.startsWith("```")) {
            require(json.endsWith("```"))
            val firstLine = json.substringBefore('\n').trim()
            require(firstLine == "```" || firstLine.equals("```json", ignoreCase = true))
            json = json.substringAfter('\n').removeSuffix("```").trim()
        }
        val tokener = JSONTokener(json)
        val value = tokener.nextValue()
        require(tokener.nextClean() == '\u0000')
        return value
    }

    private inline fun <T> checked(route: String, block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        fail(route, "模型返回格式不符合要求，请重试")
    }

    private fun fail(route: String, message: String): Nothing = throw ApiException(route, null, message)
}
