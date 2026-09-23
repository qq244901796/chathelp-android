package com.jev.probe.jev

import org.json.JSONObject

/** At most one regeneration for schema errors, without weakening validation. */
internal object StructuredCompletion {
    fun <T> request(body: JSONObject, route: String, post: (JSONObject) -> JSONObject, parse: (String) -> T): T {
        var request = JSONObject(body.toString())
        for (attempt in 0..1) {
            // Transport/auth/filter errors are not formatting errors and do not regenerate.
            val content = ModelJson.content(post(request), route)
            try {
                return parse(content)
            } catch (e: ModelFormatException) {
                if (attempt == 1) throw e
                request = JSONObject(body.toString())
                val system = request.getJSONArray("messages").getJSONObject(0)
                system.put("content", system.getString("content") +
                    "\n上一轮输出未通过校验：${e.detail}。请纠正这一点，重新给出完整 JSON，其他格式要求保持不变。")
                request.put("temperature", 0.0)
            }
        }
        error("Unreachable")
    }
}
