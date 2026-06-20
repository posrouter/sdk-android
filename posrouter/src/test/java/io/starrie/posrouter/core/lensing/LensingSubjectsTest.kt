package io.starrie.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensingSubjectsTest {
    @Test
    fun paySubjectFormat() {
        assertEquals("lensing.terminal.TID001.pay", LensingSubjects.paySubject("TID001"))
    }

    @Test
    fun resultSubjectFormat() {
        assertEquals("lensing.terminal.TID001.result", LensingSubjects.resultSubject("TID001"))
    }
}
