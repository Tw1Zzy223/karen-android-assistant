package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class ModeCommandsTest {
    @Test fun jarvisCommandsKeepAppName() {
        for (name in listOf("Джарвис", "Jarvis", "Karen", "Карен")) {
            val command = IntentParser.parse("$name пожалуйста открой телеграм")
            assertEquals(CommandKind.OPEN_APP, command.kind)
            assertEquals("телеграм", command.argument)
        }
    }
    @Test fun jarvisRelativeVolume() {
        assertEquals(-15, IntentParser.parse("Джарвис сделай потише").delta)
        assertEquals(50, IntentParser.parse("Jarvis громкость пятьдесят").percent)
    }
    @Test fun identityFollowsSelectedMode() {
        assertTrue(LocalAnswers.answer("кто ты", "Джарвис")!!.contains("Я Джарвис"))
        assertTrue(LocalAnswers.answer("как тебя зовут")!!.contains("Я Карен"))
        assertEquals(LocalAnswers.answer("12 плюс 8"), LocalAnswers.answer("12 плюс 8", "Джарвис"))
    }
}
