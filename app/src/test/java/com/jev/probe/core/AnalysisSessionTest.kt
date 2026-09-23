package com.jev.probe.core

import org.junit.Assert.*
import org.junit.Test

class AnalysisSessionTest {
    @Test fun sameTextInDifferentChatsDoesNotReuseResults() {
        val session = AnalysisSession()
        session.select("微信", "联系人甲", "你好")
        val first = session.begin()
        assertFalse(session.select("微信", "联系人甲", "你好"))
        assertTrue(session.isCurrent(first))
        assertTrue(session.select("微信", "联系人乙", "你好"))
        assertFalse(session.isCurrent(first))
        session.select("微信", "联系人甲", "你好")
        assertFalse(session.isCurrent(first))
    }

    @Test fun newMessagesManualRefreshAndLeavingAppInvalidateCallbacks() {
        val session = AnalysisSession()
        session.select("QQ", "甲", "消息一")
        val first = session.begin()
        session.select("QQ", "甲", "消息二")
        assertFalse(session.isCurrent(first))
        val second = session.begin()
        val third = session.begin()
        assertFalse(session.isCurrent(second))
        assertTrue(session.isCurrent(third))
        session.invalidate()
        assertFalse(session.isCurrent(third))
    }
}
