package com.posrouter.terminal

internal object TerminalUiForegroundTracker {
    @Volatile
    var isForeground: Boolean = false
}
