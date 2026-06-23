package com.posrouter.core.lensing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoidedAttemptRegistryTest {

    @Test
    fun markAndCheckVoided() {
        assertFalse(VoidedAttemptRegistry.isVoided("TID001", "ORD_VOID_1", "ORD_VOID_1#1"))
        VoidedAttemptRegistry.mark("TID001", "ORD_VOID_1", "ORD_VOID_1#1")
        assertTrue(VoidedAttemptRegistry.isVoided("TID001", "ORD_VOID_1", "ORD_VOID_1#1"))
    }
}
