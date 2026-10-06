package com.posrouter

import com.posrouter.core.lensing.LensingSubjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusQueryTest {

    private val config = POSRouterConfig(
        participantCode = "GPOS",
        participantKey = "key",
        terminalId = "LANE01",
        acquirerCode = "supy",
        merchantId = "abc123"
    )

    @Test
    fun nullTerminalBroadcastsMerchantWide() {
        val wire = StatusQueryRequest(orderId = "ORD-1", subMerchantId = "REST01").toWire(config)

        assertTrue(wire.isBroadcast)
        assertEquals(
            "lensing.SUPY.abc123._._ALL.query",
            LensingSubjects.querySubject(wire.subjectScope())
        )
        assertEquals("REST01", wire.subMerchantId)
        assertEquals("GPOS", wire.requestedBy)
    }

    @Test
    fun targetedQueryUsesTerminalNamespace() {
        val wire = StatusQueryRequest(orderId = "ORD-1", terminalId = "TID001", subMerchantId = "REST01")
            .toWire(config)

        assertFalse(wire.isBroadcast)
        assertEquals(
            "lensing.SUPY.abc123.REST01.TID001.query",
            LensingSubjects.querySubject(wire.subjectScope())
        )
    }

    @Test
    fun wireRoundTrip() {
        val wire = StatusQueryRequest(
            orderId = "ORD-1",
            terminalId = "TID001",
            attemptId = "ORD-1#refund",
            operation = "REFUND"
        ).toWire(config)

        val parsed = WireStatusQuery.fromJson(wire.toJsonString())

        assertEquals(wire, parsed)
        assertEquals(StatusQueryRequest.OPERATION_REFUND, parsed?.operation)
    }

    @Test
    fun optionalFieldsStayNull() {
        val parsed = WireStatusQuery.fromJson(StatusQueryRequest(orderId = "ORD-2").toWire(config).toJsonString())

        assertNull(parsed?.attemptId)
        assertNull(parsed?.operation)
        assertNull(parsed?.subMerchantId)
    }

    @Test
    fun fromJsonRejectsPayloadWithoutQueryId() {
        assertNull(WireStatusQuery.fromJson("""{"orderId":"ORD-1","acquirerCode":"SUPY","merchantId":"abc123"}"""))
    }

    @Test
    fun rejectsInvalidRequests() {
        assertThrows(IllegalArgumentException::class.java) {
            StatusQueryRequest(orderId = " ").toWire(config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StatusQueryRequest(orderId = "ORD-1", operation = "void").toWire(config)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StatusQueryRequest(orderId = "ORD-1", terminalId = LensingSubjects.BROADCAST_TERMINAL_ID).toWire(config)
        }
    }
}
