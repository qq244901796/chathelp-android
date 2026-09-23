package com.jev.probe.jev

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs

/** Contains only predefined field names/rules, never the model's raw output. */
internal class ModelFormatException(route: String, val detail: String) :
    ApiException(route, null, "模型结果格式异常：$detail")

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
        rule(array.length() == 3, Route.REPLY, "需要 3 条候选回复")
        val replies = (0..2).map {
            val text = (array.get(it) as? String)?.trim()
                ?: throw ModelFormatException(Route.REPLY, "候选回复必须是字符串")
            rule(text.isNotEmpty() && text.codePointCount(0, text.length) <= 40, Route.REPLY, "候选回复必须非空且不超过 40 字")
            text
        }
        rule(replies.distinct().size == 3, Route.REPLY, "候选回复不能重复")
        replies
    }

    /** Validate against the existing Jev question definitions, including every enum. */
    fun answers(content: String, questions: JSONObject, route: String = Route.JUDGE): JSONObject = checked(route) {
        val answers = (decode(content) as? JSONObject)?.optJSONObject("answers")
            ?: throw ModelFormatException(route, "缺少 answers 对象")
        val expected = questions.keys().asSequence().toSet()
        rule(answers.keys().asSequence().toSet() == expected, route, "判断项目缺失或多出了未定义的项目")
        expected.forEach { key ->
            val question = questions.getJSONObject(key)
            val answer = answers.optJSONObject(key)
                ?: throw ModelFormatException(route, "$key 必须是对象")
            when (question.getString("type")) {
                "noul" -> number(answer, "noul", 0.0, 1.0, route, "$key.noul")
                "score" -> {
                    val levels = question.getJSONArray("criteria")
                    number(answer, "score", 0.0, (levels.length() - 1).toDouble(), route, "$key.score")
                    number(answer, "confidence", 0.0, 1.0, route, "$key.confidence")
                    // Jev uses zero-based levels (0..9); do not trust a generated legend.
                    answer.put("legend", JSONObject().also { legend ->
                        for (i in 0 until levels.length()) legend.put(i.toString(), levels.getString(i))
                    })
                }
                "choice" -> {
                    val options = question.getJSONObject("criteria").keys().asSequence().toSet()
                    val selected = answer.opt("choice") as? String
                    number(answer, "confidence", 0.0, 1.0, route, "$key.confidence")
                    val probabilities = answer.optJSONObject("probabilities")
                        ?: throw ModelFormatException(route, "$key 缺少概率表")
                    rule(probabilities.keys().asSequence().toSet() == options, route, "$key 概率表需要覆盖所有规定选项")
                    val values = options.associateWith { number(probabilities, it, 0.0, 1.0, route, "$key.probabilities.$it") }
                    val total = values.values.sum()
                    rule(total > 0 && abs(total - 1.0) <= 0.05, route, "$key 概率总和需要为 1")
                    // GLM may invent a choice label even when its full probability table
                    // is valid. A unique maximum is sufficient to derive the same choice
                    // without inventing a score or translating an unknown label.
                    val maximum = values.values.max()
                    val winners = options.filter { values.getValue(it) == maximum }
                    val choice = when {
                        selected in winners -> selected!!
                        winners.size == 1 -> winners.single()
                        else -> throw ModelFormatException(route, "$key.choice 无效且最高概率并列，无法确定选项")
                    }
                    answer.put("choice", choice)
                    values.forEach { (option, probability) -> probabilities.put(option, probability / total) }
                }
                else -> error("question type")
            }
        }
        answers
    }

    private fun number(o: JSONObject, key: String, min: Double, max: Double, route: String, field: String): Double {
        val value = (o.opt(key) as? Number)?.toDouble()
            ?: throw ModelFormatException(route, "$field 必须是 JSON 数字")
        rule(value.isFinite() && value in min..max, route, "$field 必须在 $min 到 $max 之间")
        return value
    }

    private fun rule(valid: Boolean, route: String, detail: String) {
        if (!valid) throw ModelFormatException(route, detail)
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
    } catch (e: ModelFormatException) {
        throw e
    } catch (_: Exception) {
        throw ModelFormatException(route, "不是完整的预期 JSON 结构")
    }

    private fun fail(route: String, message: String): Nothing = throw ApiException(route, null, message)
}
