package com.karen.assistant

import java.util.Locale

enum class CommandKind { OPEN_APP, APP_SETTINGS, STORE, VOLUME, BRIGHTNESS, HOME, SCREENSHOT, RECORD, STOP_RECORD, TIME, DATE, BATTERY, SCAN_APPS, GREETING, HELP, ANSWER, QUESTION, UNKNOWN }
data class ParsedCommand(val kind: CommandKind, val argument: String = "", val percent: Int? = null, val delta: Int = 0)

/** Deterministic local language understanding. No remote model or API key. */
object IntentParser {
    fun normalize(raw: String): String = raw.lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9.,+*/% -]"), " ").replace(Regex("\\s+"), " ").trim()

    fun parse(raw: String): ParsedCommand {
        if (raw.startsWith("ответ:", true)) return ParsedCommand(CommandKind.ANSWER, raw.substringAfter(':').trim())
        val text = normalize(raw).replace(Regex("\\b(карен|karen|джарвис|jarvis|пожалуйста|плиз|мне|пожалуй|ну)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        val words = text.split(" ")
        if (Regex("\\bне\\s+(надо\\s+|нужно\\s+)?(откр|запус|включ|уменьш|увелич|скач|установ|запис|сверн|закр|делай)").containsMatchIn(text)) return ParsedCommand(CommandKind.ANSWER, "Хорошо, действие не выполняю.")
        fun has(vararg stems: String) = words.any { word -> stems.any { word.startsWith(it) } }
        fun action(vararg verbs: String) = words.any { word -> verbs.any { word == it || (word.length >= 5 && it.length >= 5 && distance(word, it) <= 1) } }
        fun tail(vararg verbs: String): String {
            val index = words.indexOfFirst { word -> verbs.any { word == it || (word.length >= 5 && it.length >= 5 && distance(word, it) <= 1) } }
            return words.drop(index + 1).joinToString(" ").replace(Regex("\\b(приложение|приложения|приложеньице|в|из|на|гугл|плей|google|play|магазине)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        }
        val isQuestion = Regex("^(что|кто|почему|зачем|как|расскажи|объясни)\\b").containsMatchIn(text)
        if (has("врем", "час") && !action("открой", "открыть", "запусти", "запустить", "скачай", "установи") && (has("скольк", "котор", "назов", "скажи") || text == "время")) return ParsedCommand(CommandKind.TIME)
        if (has("дат", "число", "день") && (has("сегодня", "какой", "назов", "скажи") || text == "дата")) return ParsedCommand(CommandKind.DATE)
        if (has("заряд", "батаре") && (has("скольк", "какой", "скажи", "остат") || text == "заряд")) return ParsedCommand(CommandKind.BATTERY)
        if (isQuestion || has("посчитай", "вычисли")) return ParsedCommand(CommandKind.QUESTION, raw)
        if (action("скачай", "скачать", "загрузи") || (action("установи", "установить") && !has("громк", "ярк", "звук")) || (has("найди", "поиск") && (text.contains("плей") || text.contains("play")))) return ParsedCommand(CommandKind.STORE, tail("скачай", "скачать", "установи", "установить", "загрузи", "найди", "поиск"))
        if (has("настройк") && has("приложен")) return ParsedCommand(CommandKind.APP_SETTINGS, text.substringAfter("приложения", text.substringAfter("приложение", "")).trim())
        if (action("открой", "открыть", "запусти", "запустить", "включи") && !has("звук", "запис", "экран", "громк", "ярк")) return ParsedCommand(CommandKind.OPEN_APP, tail("открой", "открыть", "запусти", "запустить", "включи"))
        for (verb in listOf("зайти в", "зайди в", "перейди в")) if (text.contains(verb)) return ParsedCommand(CommandKind.OPEN_APP, text.substringAfter(verb).trim())
        if (has("анализ", "проскан", "сканир", "список") && has("приложен")) return ParsedCommand(CommandKind.SCAN_APPS)
        if (has("запис", "запиш") && has("останов", "выключ", "закончи", "прекрат")) return ParsedCommand(CommandKind.STOP_RECORD)
        if (has("скрин", "сфотограф") || text.contains("снимок экрана") || text.contains("фото экрана")) return ParsedCommand(CommandKind.SCREENSHOT)
        if (has("запис", "запиш") && (has("экран", "видео") || action("начни", "включи", "запиши", "начать"))) return ParsedCommand(CommandKind.RECORD)
        if (has("ярк", "светлее", "темнее", "посветлее", "потемнее")) {
            val direction = when { has("уменьш", "пониз", "убав", "темнее", "потемнее") -> -1; has("увелич", "повыс", "добав", "светлее", "посветлее") -> 1; else -> 0 }
            val value = if (has("максим")) 100 else if (has("миним")) 1 else number(text)
            return if (direction != 0 && !words.contains("до")) ParsedCommand(CommandKind.BRIGHTNESS, delta = direction * (value ?: 15))
                else ParsedCommand(CommandKind.BRIGHTNESS, percent = value)
        }
        if (has("громк", "громче", "погромче", "тише", "потише", "звук")) {
            val value = when { text.contains("без звука") || (has("выключ", "отключ", "убери") && has("звук")) -> 0; has("максим") -> 100; else -> number(text) }
            val direction = when { has("уменьш", "пониз", "убав", "тише", "потише") -> -1; has("увелич", "повыс", "добав", "громче", "погромче") -> 1; else -> 0 }
            return if (direction != 0 && !words.contains("до")) ParsedCommand(CommandKind.VOLUME, delta = direction * (value ?: 15))
                else ParsedCommand(CommandKind.VOLUME, percent = value)
        }
        if (has("закрой", "закрыть", "сверни", "свернуть", "выйди") || text.contains("главный экран") || text.contains("домой")) return ParsedCommand(CommandKind.HOME)
        if (has("привет", "здравств")) return ParsedCommand(CommandKind.GREETING)
        if (text.contains("что умеешь") || has("помощь", "команды")) return ParsedCommand(CommandKind.HELP)
        if (text.startsWith("сколько") || text.contains("?")) return ParsedCommand(CommandKind.QUESTION, raw)
        return ParsedCommand(CommandKind.UNKNOWN, raw)
    }

    fun number(text: String): Int? {
        if (text.contains("половин")) return 50
        if (text.contains("четверть")) return 25
        Regex("-?\\d{1,3}").find(text)?.value?.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        val values = mapOf("ноль" to 0, "нуль" to 0, "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90, "сто" to 100)
        val numbers = normalize(text).split(" ").mapNotNull { values[it] }
        return if (numbers.isEmpty()) null else numbers.sum().coerceIn(0, 100)
    }
    fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, ca ->
            val next = IntArray(b.length + 1); next[0] = i + 1
            b.forEachIndexed { j, cb -> next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (ca == cb) 0 else 1) }
            row = next
        }
        return row[b.length]
    }
}
