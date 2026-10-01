package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test

class IntentParserTest {
    @Test fun flexibleVolume() {
        for (phrase in listOf("Карен пожалуйста сделай потише", "уменьши звук", "можешь убавить громкость")) {
            val command = IntentParser.parse(phrase)
            assertEquals(phrase, CommandKind.VOLUME, command.kind); assertEquals(-15, command.delta)
        }
        assertEquals(50, IntentParser.parse("установи громкость на пятьдесят процентов").percent)
        assertEquals(50, IntentParser.parse("звук на половину").percent)
        assertEquals(0, IntentParser.parse("выключи звук").percent)
        assertEquals(-20, IntentParser.parse("уменьши громкость на двадцать").delta)
        assertEquals(20, IntentParser.parse("уменьши громкость до двадцати 20").percent)
    }
    @Test fun brightnessAndCapture() {
        assertEquals(15, IntentParser.parse("сделай экран светлее").delta)
        assertEquals(CommandKind.BRIGHTNESS, IntentParser.parse("можно экран потемнее").kind)
        assertEquals(70, IntentParser.parse("установи яркость 70").percent)
        assertEquals(CommandKind.STOP_RECORD, IntentParser.parse("пожалуйста закончи запись экрана").kind)
        assertEquals(CommandKind.RECORD, IntentParser.parse("запиши экран").kind)
        assertEquals(CommandKind.SCREENSHOT, IntentParser.parse("сделай фото экрана").kind)
    }
    @Test fun appNamesAndTypos() {
        assertEquals("телеграм", IntentParser.parse("Карен можешь открыть приложение телеграм пожалуйста").argument)
        assertEquals(CommandKind.OPEN_APP, IntentParser.parse("открои дискорд").kind)
        assertEquals("telegram", AppNames.canonical("телега"))
        assertTrue(AppNames.score("телегрм", "телеграм", "org.telegram.messenger") < .32)
        assertTrue(AppNames.score("совсем другое", "Chrome", "com.android.chrome") > .32)
        assertTrue(AppNames.score("спотифай", "Spotify", "com.spotify.music") < .32)
    }
    @Test fun storeAndQuestionsDoNotChangeSettings() {
        assertEquals(CommandKind.STORE, IntentParser.parse("скачай мне телеграм из гугл плей").kind)
        assertEquals("телеграм", IntentParser.parse("скачай мне телеграм из гугл плей").argument)
        assertEquals(CommandKind.QUESTION, IntentParser.parse("что будет если уменьшить громкость").kind)
        assertEquals(CommandKind.QUESTION, IntentParser.parse("как установить яркость").kind)
        assertEquals(CommandKind.OPEN_APP, IntentParser.parse("открой часы").kind)
        assertEquals(CommandKind.TIME, IntentParser.parse("скажи который час").kind)
        assertEquals(CommandKind.ANSWER, IntentParser.parse("не открывай телеграм").kind)
        assertEquals(CommandKind.ANSWER, IntentParser.parse("не надо уменьшать громкость").kind)
    }
    @Test fun customPhrasesAndAlternatives() {
        val commands = mapOf("мне скучно" to "открой YouTube")
        assertEquals("открой YouTube", PhraseAliases.resolve(commands, "Карен мне скучно пожалуйста"))
        assertNull(PhraseAliases.resolve(commands, "скучно"))
        assertEquals("что такое громкость", PhraseAliases.best(listOf("что такое громкость", "громкость пятьдесят"), commands))
        assertEquals("сделай потише", PhraseAliases.best(listOf("неразборчивая фраза", "сделай потише"), commands))
    }
    @Test fun localAnswers() {
        assertEquals("Получается 20", LocalAnswers.answer("сколько будет 12 плюс 8"))
        assertEquals("Получается 42", LocalAnswers.answer("посчитай 6 умножить на 7"))
        assertEquals("На ноль делить нельзя.", LocalAnswers.answer("сколько будет 10 разделить на 0"))
        assertNull(LocalAnswers.answer("почему небо голубое"))
        assertNull(LocalAnswers.answer("сколько будет 2 плюс 3 умножить на 4"))
    }
}
