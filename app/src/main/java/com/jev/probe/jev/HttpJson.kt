package com.jev.probe.jev

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException

object Route {
    const val JUDGE = "判断接口"
    const val REPLY = "回复接口"
    const val RANK = "排序接口"
    const val VISION = "视觉接口"
}

open class ApiException(val route: String, val status: Int?, val snippet: String) :
    RuntimeException(buildMessage(route, status, snippet)) {
    companion object {
        fun buildMessage(route: String, status: Int?, snippet: String): String =
            if (status != null) "${route} HTTP ${status}：${snippet.take(120)}"
            else "${route} 请求失败：${snippet.take(120)}"
    }
}

object HttpJson {
    private val client = JsonHttpClient()

    fun post(url: String, key: String, body: JSONObject, route: String,
             extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        if (key.isBlank()) throw ApiException(route, null, "请为该服务商填写 API Key")
        val request = { client.post(url, key, body, route, extraHeaders) }
        return if (RequestGate.isBigModel(url)) RequestGate.bigModel.run(request) else request()
    }

    fun headersFor(url: String): Map<String, String> =
        if (runCatching { URL(url).host.equals("openrouter.ai", true) }.getOrDefault(false))
            mapOf("HTTP-Referer" to "https://jev-assistant.local", "X-Title" to "Jev Assistant")
        else emptyMap()
}

/** Injectable timeouts/sleeper allow real local HTTP tests without API credentials. */
internal class JsonHttpClient(
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 40000,
    private val pause: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun post(url: String, key: String, body: JSONObject, route: String,
             extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        var last: ApiException? = null
        for (attempt in 0 until 3) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            var connection: HttpURLConnection? = null
            var retryAfter: Long? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    instanceFollowRedirects = false // Never forward an API key to another host.
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${key}")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    // Retain status even when a server supplies no error body.
                    val errorBody = runCatching {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    val error = ApiException(route, status, errorMessage(status, errorBody, key))
                    if (status != 429 && status !in 500..599) throw error
                    last = error
                    retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        ?.coerceIn(1, 30)?.times(1000)
                } else {
                    val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (text.isBlank()) throw ApiException(route, status, "响应体为空，请重试")
                    return try { JSONObject(text) }
                    catch (_: Exception) { throw ApiException(route, status, "响应不是有效 JSON，请检查接口地址") }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                last = ApiException(route, null, when (e) {
                    is SocketTimeoutException -> "网络超时，请检查连接或稍后重试"
                    is java.net.UnknownHostException -> "域名解析失败，请检查接口地址和网络"
                    is javax.net.ssl.SSLException -> "HTTPS 证书校验失败"
                    else -> "无法连接服务，请检查网络和接口地址"
                })
            } catch (_: IllegalArgumentException) {
                throw ApiException(route, null, "接口地址无效")
            } finally {
                connection?.disconnect()
            }
            if (attempt < 2) pause(retryAfter ?: (1000L shl attempt))
        }
        throw last ?: ApiException(route, null, "请求失败")
    }

    private fun errorMessage(status: Int, body: String, key: String): String {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code").orEmpty().take(24)
        val message = when (status) {
            401, 403 -> "密钥无效或无调用权限，请检查该服务商的 API Key"
            429 -> "请求受限，请稍后重试"
            in 500..599 -> "服务暂时不可用，请稍后重试"
            in 300..399 -> "接口返回重定向，请填写服务商的最终 HTTPS 地址"
            else -> "请求未被接受，请检查模型名、参数和账户权限"
        }
        // No raw server error body: it may echo the prompt or credentials.
        val safeCode = if (code.matches(Regex("[A-Za-z0-9_-]{1,24}"))) code else ""
        return (message + if (safeCode.isNotEmpty()) "（错误码 ${safeCode}）" else "")
            .let { if (key.isNotEmpty()) it.replace(key, "[已隐藏]") else it }
    }
}
