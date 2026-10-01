package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class RecognitionSessionTest {
    private class Clock : SpeechScheduler {
        var now = 0L
        data class Job(val at: Long, val task: () -> Unit, var cancelled: Boolean = false)
        private val jobs = mutableListOf<Job>()
        override fun after(delayMs: Long, task: () -> Unit): CancelTask {
            val job = Job(now + delayMs, task); jobs.add(job)
            return CancelTask { job.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                jobs.remove(job); now = job.at; job.task()
            }
            now = end
        }
    }
    private class Fixture {
        val clock = Clock()
        val events = mutableListOf<SpeechEvents>()
        val backends = mutableListOf<Boolean>()
        val messages = mutableListOf<Pair<Boolean, String>>()
        val commands = mutableListOf<List<String>>()
        var closed = 0
        val session = RecognitionSession(clock, { local, callbacks ->
            events.add(callbacks); backends.add(local)
            object : SpeechTransport { override fun start() {} ; override fun close() { closed++ } }
        }, { active, message -> messages.add(active to message) }, { commands.add(it) })
        fun start(local: Boolean = false): SpeechEvents { session.start(local); clock.advance(400); return events.last() }
    }
    @Test fun processingAlwaysTimesOutWithoutExecutingPartialSpeech() {
        val f = Fixture(); val e = f.start()
        e.ready(); e.beginning(); e.partial(listOf("открой")); e.ended()
        f.clock.advance(7000)
        assertFalse(f.session.active); assertEquals(1, f.closed); assertTrue(f.commands.isEmpty())
        assertTrue(f.messages.last().second.contains("открой"))
        e.results(listOf("открой Telegram"))
        assertTrue(f.commands.isEmpty())
    }
    @Test fun oldCallbacksCannotFinishANewSession() {
        val f = Fixture(); val old = f.start(); f.session.cancel()
        val current = f.start()
        old.results(listOf("громкость 0")); old.error(9)
        assertTrue(f.session.active); assertTrue(f.commands.isEmpty())
        current.results(listOf("громкость 50")); current.results(listOf("громкость 0"))
        assertEquals(listOf(listOf("громкость 50")), f.commands)
    }
    @Test fun failedLocalEngineFallsBackOnceThenStops() {
        val f = Fixture(); val local = f.start(true)
        local.error(13); f.clock.advance(700)
        assertEquals(listOf(true, false), f.backends)
        local.results(listOf("поздний ответ")); assertTrue(f.commands.isEmpty())
        f.events.last().error(11)
        f.clock.advance(40000)
        assertFalse(f.session.active); assertEquals(2, f.events.size)
    }
    @Test fun cancelStopsPendingRetryAndDeadline() {
        val f = Fixture(); val e = f.start(); e.error(8); f.session.cancel()
        val count = f.messages.size
        f.clock.advance(40000)
        assertEquals(1, f.events.size); assertFalse(f.session.active); assertEquals(count, f.messages.size)
    }
    @Test fun silenceAndStartupAreBounded() {
        val startup = Fixture(); startup.start(); startup.clock.advance(8000)
        assertFalse(startup.session.active)
        val silence = Fixture(); silence.start().ready(); silence.clock.advance(14000)
        assertFalse(silence.session.active)
    }
    @Test fun partialsCannotExtendOverallDeadline() {
        val f = Fixture(); val e = f.start(); e.ready()
        repeat(5) { e.beginning(); e.partial(listOf("команда $it")); f.clock.advance(5000) }
        f.clock.advance(5000)
        assertFalse(f.session.active); assertTrue(f.commands.isEmpty())
    }
    @Test fun permissionDeniedDoesNotRetry() {
        val f = Fixture(); f.start().error(9); f.clock.advance(40000)
        assertFalse(f.session.active); assertEquals(1, f.events.size)
        assertTrue(f.messages.last().second.contains("Разрешите"))
    }
    @Test fun noMatchAllowsOneNewUtterance() {
        val f = Fixture(); f.start().error(7); f.clock.advance(700)
        f.events.last().ready(); f.events.last().results(listOf("сделай потише"))
        assertFalse(f.session.active); assertEquals(listOf(listOf("сделай потише")), f.commands)
    }
    @Test fun rejectedSpeakerNeverFallsBackToUnverifiedRecognition() {
        val f = Fixture(); f.start(true).error(1001); f.clock.advance(40000)
        assertEquals(listOf(true), f.backends); assertTrue(f.commands.isEmpty()); assertFalse(f.session.active)
    }
}
