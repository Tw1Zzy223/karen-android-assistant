package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class CommandNumbersTest {
    @Test fun spokenPercentages() {
        assertEquals(50, CommandEngine.percent("громкость пятьдесят"))
        assertEquals(75, CommandEngine.percent("яркость семьдесят пять процентов"))
        assertEquals(0, CommandEngine.percent("звук ноль"))
        assertEquals(100, CommandEngine.percent("яркость сто"))
        assertEquals(19, CommandEngine.percent("громкость девятнадцать"))
    }
    @Test fun digitsAndBounds() {
        assertEquals(55, CommandEngine.percent("громкость 55"))
        assertEquals(100, CommandEngine.percent("яркость 150"))
    }
    @Test fun relativeCommandsAreNotNumbers() {
        assertNull(CommandEngine.percent("сделай громче"))
        assertNull(CommandEngine.percent("сделай тише"))
    }
}
