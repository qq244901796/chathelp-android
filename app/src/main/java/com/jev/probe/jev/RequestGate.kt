package com.jev.probe.jev

import java.net.URI
import java.util.concurrent.locks.ReentrantLock

/** One process-wide lane for BigModel, including tests, replies, ranking and summaries. */
internal class RequestGate {
    private val lock = ReentrantLock(true)

    fun <T> run(block: () -> T): T {
        lock.lockInterruptibly()
        try {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            return block()
        } finally {
            lock.unlock()
        }
    }

    companion object {
        val bigModel = RequestGate()
        fun isBigModel(url: String): Boolean =
            runCatching { URI(url).host.equals("open.bigmodel.cn", ignoreCase = true) }.getOrDefault(false)
    }
}
