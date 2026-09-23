package com.jev.probe.jev

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HttpJsonTest {
    private fun withServer(status: (Int) -> Int, test: (String, AtomicInteger) -> Unit) {
        val calls = AtomicInteger()
        val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val executor = Executors.newSingleThreadExecutor()
        val serving = executor.submit {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                socket.use {
                    socket.soTimeout = 2000
                    val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    assertTrue(reader.readLine().startsWith("POST /chat/completions"))
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                    }
                    val text = CharArray(headers.getValue("content-length").toInt())
                    var offset = 0
                    while (offset < text.size) {
                        val n = reader.read(text, offset, text.size - offset)
                        require(n > 0)
                        offset += n
                    }
                    assertEquals("glm-4-flash-250414", JSONObject(String(text)).getString("model"))
                    assertEquals("Bearer test-key", headers["authorization"])
                    val code = status(calls.incrementAndGet())
                    val response = if (code == 200) "{\"ok\":true}" else
                        "{\"error\":{\"code\":\"1302\",\"message\":\"private prompt and test-key\"}}"
                    val bytes = response.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 ${code} Test\r\nContent-Length: ${bytes.size}\r\n" +
                        "Content-Type: application/json\r\nRetry-After: 2\r\n" +
                        "Location: /chat/completions\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                }
            }
        }
        try { test("http://127.0.0.1:${server.localPort}/chat/completions", calls) }
        finally {
            server.close()
            try { serving.get(3, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
        }
    }

    private val body get() = JSONObject().put("model", "glm-4-flash-250414")

    @Test fun succeedsAfterRateLimitAndRespectsRetryAfter() {
        val waits = mutableListOf<Long>()
        withServer({ if (it == 1) 429 else 200 }) { url, count ->
            assertTrue(JsonHttpClient(pause = { waits.add(it) }).post(url, "test-key", body, Route.JUDGE).getBoolean("ok"))
            assertEquals(2, count.get())
            assertEquals(listOf(2000L), waits)
        }
    }

    @Test fun retriesAreBoundedAndErrorsDoNotExposePromptsOrKeys() {
        withServer({ 503 }) { url, count ->
            val error = assertThrows(ApiException::class.java) {
                JsonHttpClient(pause = {}).post(url, "test-key", body, Route.REPLY)
            }
            assertEquals(3, count.get())
            assertEquals(503, error.status)
            assertFalse(error.message!!.contains("private prompt"))
            assertFalse(error.message!!.contains("test-key"))
        }
    }

    @Test fun noRetryForAuthenticationBadRequestOrRedirect() {
        listOf(400, 401, 403, 302).forEach { status ->
            withServer({ status }) { url, count ->
                val error = assertThrows(ApiException::class.java) {
                    JsonHttpClient(pause = { fail("must not retry") }).post(url, "test-key", body, Route.REPLY)
                }
                assertEquals(status, error.status)
                assertEquals(1, count.get())
            }
        }
    }

    @Test fun timeoutIsReportedAndRetriedAtMostThreeTimes() {
        val server = java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        server.use {
            val error = assertThrows(ApiException::class.java) {
                JsonHttpClient(connectTimeoutMs = 100, readTimeoutMs = 80, pause = {})
                    .post("http://127.0.0.1:${server.localPort}/chat/completions", "test-key", body, Route.REPLY)
            }
            assertNull(error.status)
            assertTrue(error.message!!.contains("超时"))
        }
    }

    @Test fun allConcurrentTasksShareOneLane() {
        val gate = RequestGate()
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        try {
            val futures = (1..6).map {
                pool.submit {
                    start.await()
                    gate.run {
                        val n = active.incrementAndGet()
                        maximum.updateAndGet { old -> maxOf(old, n) }
                        Thread.sleep(15)
                        active.decrementAndGet()
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, maximum.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun interruptedWaitingRequestNeverRuns() {
        val gate = RequestGate()
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val owner = Thread { gate.run { held.countDown(); release.await() } }
        owner.start()
        assertTrue(held.await(2, TimeUnit.SECONDS))
        val cancelled = CountDownLatch(1)
        val called = AtomicInteger()
        val waiter = Thread {
            try { gate.run { called.incrementAndGet() } }
            catch (_: InterruptedException) { cancelled.countDown() }
        }
        try {
            waiter.start()
            waiter.interrupt()
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            assertEquals(0, called.get())
        } finally {
            release.countDown()
            owner.join(2000)
            waiter.join(2000)
        }
    }

    @Test fun bigModelHostCheckDoesNotMatchLookalikes() {
        assertTrue(RequestGate.isBigModel("https://open.bigmodel.cn/api/paas/v4/chat/completions"))
        assertFalse(RequestGate.isBigModel("https://open.bigmodel.cn.example.com"))
        assertFalse(RequestGate.isBigModel("https://example.com/open.bigmodel.cn"))
        assertFalse(RequestGate.isBigModel("bad address"))
    }
}
