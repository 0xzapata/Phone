package org.fossify.phone.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayClientTest {
    // 2026-10-12T07:00:00.250Z: the milliseconds must be cut, the zero seconds must stay.
    private val sevenAm = 1_791_788_400_250L

    @Test
    fun smsEventJsonHasExactlyTheContractFields() {
        val event = GatewayEvent(
            id = "1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f",
            kind = SMS_RECEIVED,
            occurredAtMs = sevenAm,
            number = "+4512345678",
            displayName = "Alice",
            body = "Your code is 123456"
        )
        val json = JSONObject(eventJson(event))
        assertEquals(setOf("id", "kind", "occurred_at", "number", "display_name", "body"), keysOf(json))
        assertEquals("1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f", json.get("id"))
        assertEquals("sms_received", json.get("kind"))
        assertEquals("2026-10-12T07:00:00Z", json.get("occurred_at"))
        assertEquals("+4512345678", json.get("number"))
        assertEquals("Alice", json.get("display_name"))
        assertEquals("Your code is 123456", json.get("body"))
    }

    @Test
    fun callEventJsonHasExactlyTheContractFields() {
        val event = GatewayEvent(
            id = "2c5f39cb-3ab2-4e4f-8d7b-8a1b2c3d4e5f",
            kind = CALL_ENDED,
            occurredAtMs = sevenAm + 330_000L,
            number = "+4512345678",
            displayName = "",
            direction = "incoming",
            durationSeconds = 42,
            missed = false
        )
        val json = JSONObject(eventJson(event))
        val keys = setOf("id", "kind", "occurred_at", "number", "display_name", "direction", "duration_s", "missed")
        assertEquals(keys, keysOf(json))
        assertEquals("2c5f39cb-3ab2-4e4f-8d7b-8a1b2c3d4e5f", json.get("id"))
        assertEquals("call_ended", json.get("kind"))
        assertEquals("2026-10-12T07:05:30Z", json.get("occurred_at"))
        assertEquals("+4512345678", json.get("number"))
        assertEquals("", json.get("display_name"))
        assertEquals("incoming", json.get("direction"))
        assertEquals(42, json.get("duration_s"))
        assertEquals(false, json.get("missed"))
    }

    @Test
    fun gatewayUrlMustBeHttps() {
        assertFalse(isGatewayUrl("http://phone-gateway.tail1234.ts.net"))
        assertFalse(isGatewayUrl(""))
        assertTrue(isGatewayUrl("https://phone-gateway.tail1234.ts.net"))
    }

    @Test
    fun emptyAllowlistSendsEveryNumberElseOnlyListedOnes() {
        assertTrue(isAllowed("+4599999999", emptySet()))
        assertTrue(isAllowed("+4512345678", setOf("+4512345678")))
        assertFalse(isAllowed("+4599999999", setOf("+4512345678")))
    }

    @Test
    fun missedOnlyForIncomingCallThatNeverConnected() {
        assertTrue(isMissed(CALL_ENDED, outgoing = false, connectTimeMillis = 0L))
        assertFalse(isMissed(CALL_ENDED, outgoing = false, connectTimeMillis = 1L))
        assertFalse(isMissed(CALL_ENDED, outgoing = true, connectTimeMillis = 0L))
        assertFalse(isMissed(CALL_STARTED, outgoing = false, connectTimeMillis = 0L))
    }

    private fun keysOf(json: JSONObject) = json.keys().asSequence().toSet()
}
