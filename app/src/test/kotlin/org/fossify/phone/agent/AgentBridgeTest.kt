package org.fossify.phone.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBridgeTest {
    @Test
    fun missedOnlyForIncomingCallThatNeverConnected() {
        assertTrue(isMissed(CALL_ENDED, outgoing = false, connectTimeMillis = 0L))
        assertFalse(isMissed(CALL_ENDED, outgoing = false, connectTimeMillis = 1L))
        assertFalse(isMissed(CALL_ENDED, outgoing = true, connectTimeMillis = 0L))
        assertFalse(isMissed(CALL_STARTED, outgoing = false, connectTimeMillis = 0L))
    }
}
