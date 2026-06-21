package com.posrouter.core.lensing

object LensingSubjects {
    fun paySubject(terminalId: String): String = "lensing.terminal.$terminalId.pay"
    fun resultSubject(terminalId: String): String = "lensing.terminal.$terminalId.result"
    fun claimedSubject(terminalId: String): String = "lensing.terminal.$terminalId.claimed"
}
