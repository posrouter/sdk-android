package com.posrouter.core.lensing

import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentResultLedgerTest {

    @Test
    fun markIfFirstAcceptsOncePerAttempt() {
        val result = PaymentResult(
            terminalId = "TID001",
            orderId = "LEDGER_ORD1",
            attemptId = "LEDGER_ORD1#1",
            status = PaymentStatus.CANCELLED,
            transactionId = null,
            amount = 100,
            currency = "NZD",
            message = null
        )
        assertTrue(PaymentResultLedger.markIfFirst(result))
        assertFalse(PaymentResultLedger.markIfFirst(result))
    }

    @Test
    fun differentAttemptIdsAreDistinct() {
        val base = PaymentResult(
            terminalId = "TID001",
            orderId = "LEDGER_ORD2",
            status = PaymentStatus.CANCELLED,
            transactionId = null,
            amount = 100,
            currency = "NZD",
            message = null
        )
        assertTrue(PaymentResultLedger.markIfFirst(base.copy(attemptId = "LEDGER_ORD2#1")))
        assertTrue(PaymentResultLedger.markIfFirst(base.copy(attemptId = "LEDGER_ORD2#2")))
    }
}
