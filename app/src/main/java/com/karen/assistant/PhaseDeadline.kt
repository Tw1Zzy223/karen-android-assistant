package com.karen.assistant

/** Progress never extends a phase deadline. A stale timer cannot end a new phase. */
class PhaseDeadline(private val scheduler: SpeechScheduler, private val timeout: (String) -> Unit) {
    private var timer: CancelTask? = null
    private var revision = 0
    fun phase(milliseconds: Long, message: String) {
        finish()
        val token = revision
        timer = scheduler.after(milliseconds) {
            if (revision == token) { finish(); timeout(message) }
        }
    }
    fun finish() { revision++; timer?.cancel(); timer = null }
}
