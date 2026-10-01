package com.karen.assistant

interface SpeechTransport {
    fun start()
    fun close()
}
interface SpeechEvents {
    fun ready()
    fun beginning()
    fun ended()
    fun partial(phrases: List<String>)
    fun results(phrases: List<String>)
    fun error(code: Int)
}
fun interface CancelTask { fun cancel() }
fun interface SpeechScheduler { fun after(delayMs: Long, task: () -> Unit): CancelTask }

/** Bounded, cancellable session independent of Android so race conditions can be tested. */
class RecognitionSession(
    private val scheduler: SpeechScheduler,
    private val factory: (Boolean, SpeechEvents) -> SpeechTransport?,
    private val state: (Boolean, String) -> Unit,
    private val result: (List<String>) -> Unit
) {
    var active = false
        private set
    private var generation = 0
    private var transport: SpeechTransport? = null
    private var phaseTimer: CancelTask? = null
    private var totalTimer: CancelTask? = null
    private var retryTimer: CancelTask? = null
    private var local = false
    private var retries = 0
    private var partial: List<String> = emptyList()
    private var processing = false

    fun start(preferLocal: Boolean) {
        cancel(false)
        active = true; local = preferLocal; retries = 0
        state(true, "Включаю микрофон…")
        totalTimer = scheduler.after(30000) { finish("Служба речи не ответила за 30 секунд. Нажмите микрофон и повторите") }
        // A short release interval prevents a just-cancelled service holding the microphone.
        retryTimer = scheduler.after(400) { attempt() }
    }
    private fun attempt() {
        if (!active) return
        val token = ++generation
        partial = emptyList(); processing = false
        val events = object : SpeechEvents {
            private fun valid() = active && generation == token
            override fun ready() {
                if (!valid() || processing) return
                phase(14000, "Не услышала речь. Нажмите микрофон и повторите")
                state(true, if (local) "Слушаю… Распознавание на телефоне" else "Слушаю… Говорите команду")
            }
            override fun beginning() { if (valid() && !processing) { phase(20000, "Долгая фраза. Нажмите микрофон и скажите команду короче"); state(true, "Слышу речь…") } }
            override fun ended() { if (valid() && !processing) { processing = true; phase(7000, "Не дождалась результата"); state(true, "Обрабатываю… Можно нажать кнопку для отмены") } }
            override fun partial(phrases: List<String>) {
                if (!valid()) return
                val newPhrases = phrases.filter { it.isNotBlank() }
                if (newPhrases.isNotEmpty() && newPhrases != partial) {
                    partial = newPhrases
                    state(true, (if (processing) "Обрабатываю: " else "Слышу: ") + partial.first())
                }
            }
            override fun results(phrases: List<String>) {
                if (!valid()) return
                val usable = phrases.filter { it.isNotBlank() }
                if (usable.isEmpty()) { error(7); return }
                finish("Вы: ${usable.first()}")
                result(usable)
            }
            override fun error(code: Int) {
                if (!valid()) return
                // Never automatically execute interim speech: it may be incomplete.
                if (retries == 0 && code in setOf(1, 2, 4, 5, 6, 7, 8, 11, 12, 13)) {
                    retries++; generation++; phaseTimer?.cancel()
                    closeTransport()
                    val repeat = code == 6 || code == 7
                    if (local && !repeat) local = false
                    state(true, if (repeat) "Не расслышала. Повторите после надписи «Слушаю»" else "Перезапускаю службу речи… Повторите после надписи «Слушаю»")
                    retryTimer = scheduler.after(700) { attempt() }
                } else finish(message(code))
            }
        }
        phase(8000, "Служба речи не включила микрофон")
        try {
            transport = factory(local, events)
            if (transport == null) { events.error(12); return }
            transport!!.start()
        } catch (_: SecurityException) { events.error(9) }
        catch (_: Exception) { events.error(11) }
    }
    private fun phase(delay: Long, message: String) {
        phaseTimer?.cancel()
        val token = generation
        phaseTimer = scheduler.after(delay) {
            if (!active || generation != token) return@after
            val hint = partial.firstOrNull()?.let { " Услышала только «$it». Повторите команду." } ?: ". Нажмите микрофон и повторите."
            finish(message + hint)
        }
    }
    private fun message(code: Int) = when (code) {
        1001 -> "Голос не совпал с образцом владельца. Команда не выполнена"
        1002 -> "Недостаточно речи для проверки голоса. Скажите команду длиной 3–5 секунд"
        1003 -> "Локальная модель не готова. Откройте «Только мой голос» для подготовки моделей"
        1, 2 -> "Нет связи со службой речи. Проверьте интернет или русский офлайн-пакет Android"
        3 -> "Микрофон занят или недоступен. Закройте приложение, использующее микрофон"
        6, 7 -> "Не расслышала. Попробуйте произнести команду после надписи «Слушаю»"
        8 -> "Служба речи занята. Нажмите микрофон ещё раз через несколько секунд"
        9 -> "Разрешите микрофон в настройках Карен"
        10 -> "Служба речи ограничила частые запросы. Подождите немного"
        12, 13 -> "Русская модель речи недоступна. Включите русский язык в настройках службы речи Android"
        else -> "Служба речи отключилась (ошибка $code). Нажмите микрофон ещё раз"
    }
    private fun closeTransport() {
        val old = transport; transport = null
        try { old?.close() } catch (_: Exception) {}
    }
    private fun cleanup() {
        generation++; active = false
        phaseTimer?.cancel(); totalTimer?.cancel(); retryTimer?.cancel()
        phaseTimer = null; totalTimer = null; retryTimer = null
        closeTransport()
    }
    private fun finish(message: String) { if (!active) return; cleanup(); state(false, message) }
    fun cancel(notify: Boolean = true) { val wasActive = active; cleanup(); if (notify && wasActive) state(false, "Микрофон выключен") }
}
