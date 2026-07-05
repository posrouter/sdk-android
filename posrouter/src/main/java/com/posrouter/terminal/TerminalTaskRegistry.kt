package com.posrouter.terminal

/** Last known Android task id for the terminal UI — used to [android.app.ActivityManager.moveTaskToFront]. */
internal object TerminalTaskRegistry {
    @Volatile
    var taskId: Int = -1
}
