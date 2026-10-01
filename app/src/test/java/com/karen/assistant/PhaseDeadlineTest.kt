package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class PhaseDeadlineTest {
    private class Clock : SpeechScheduler {
        val tasks = mutableListOf<() -> Unit>()
        val delays = mutableListOf<Long>()
        override fun after(delayMs: Long, task: () -> Unit): CancelTask {
            tasks.add(task); delays.add(delayMs)
            // Intentionally allow firing cancelled callbacks to test races.
            return CancelTask { }
        }
    }
    @Test fun modelLoadTimeoutIsTerminalOnce() {
        val clock = Clock(); val errors = mutableListOf<String>()
        val deadline = PhaseDeadline(clock, errors::add)
        deadline.phase(90000, "load timeout")
        clock.tasks[0](); clock.tasks[0]()
        assertEquals(listOf("load timeout"), errors)
        assertEquals(listOf(90000L), clock.delays)
    }
    @Test fun stopPreventsLateTimeout() {
        val clock = Clock(); val errors = mutableListOf<String>()
        val deadline = PhaseDeadline(clock, errors::add)
        deadline.phase(90000, "load"); deadline.finish(); clock.tasks[0]()
        assertTrue(errors.isEmpty())
    }
    @Test fun oldLoadTimerCannotStopSynthesis() {
        val clock = Clock(); val errors = mutableListOf<String>()
        val deadline = PhaseDeadline(clock, errors::add)
        deadline.phase(90000, "load"); deadline.phase(120000, "generation")
        clock.tasks[0](); assertTrue(errors.isEmpty())
        clock.tasks[1](); assertEquals(listOf("generation"), errors)
    }
    @Test fun completedJobDoesNotTimeout() {
        val clock = Clock(); val errors = mutableListOf<String>()
        val deadline = PhaseDeadline(clock, errors::add)
        deadline.phase(120000, "generation"); deadline.finish(); clock.tasks[0]()
        assertTrue(errors.isEmpty())
    }
}
