package com.jev.probe.core

/** Tokens survive A -> B -> A switches: old callbacks can never become current again. */
internal class AnalysisSession {
    private var identity: List<String?>? = null
    @Volatile var token: Long = 0
        private set

    @Synchronized fun select(pkg: String, title: String?, signature: String): Boolean {
        val next = listOf(pkg, title, signature)
        if (identity == next) return false
        identity = next
        token++
        return true
    }

    @Synchronized fun begin(): Long = ++token

    @Synchronized fun invalidate() {
        identity = null
        token++
    }

    fun isCurrent(candidate: Long): Boolean = token == candidate
}
