package com.karen.assistant

object LocalAnswers {
    fun answer(raw: String): String? {
        val text = IntentParser.normalize(raw)
        if (text.contains("кто ты") || text.contains("как тебя зовут")) return "Я Карен, твой голосовой помощник для управления телефоном."
        if (text.contains("что умеешь") || text.contains("что ты умеешь")) return "Открываю приложения, меняю громкость и яркость, делаю снимки и записи экрана. Можно добавить свои команды."
        if (text.contains("что такое громкость")) return "Громкость определяет, насколько громко звучит музыка и другие звуки телефона."
        if (text.contains("что такое яркость")) return "Яркость определяет, насколько светлым будет экран. Чем она выше, тем больше расход батареи."
        if (text.contains("как добавить команд")) return "Нажми «Мои команды», затем «Добавить». Введи свою фразу и выбери действие."
        val expression = text.replace("умножить на", "*").replace("разделить на", "/").replace("плюс", "+").replace("минус", "-").replace("х", "*")
        val match = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*([+*/-])\\s*(-?\\d+(?:[.,]\\d+)?)").find(expression) ?: return null
        if (Regex("[+*/-]\\s*\\d").containsMatchIn(expression.substring(match.range.last + 1))) return null
        val a = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val b = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return null
        if (match.groupValues[2] == "/" && b == 0.0) return "На ноль делить нельзя."
        val value = when (match.groupValues[2]) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> a / b }
        if (!value.isFinite()) return "Это число слишком большое."
        return "Получается " + if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')
    }
}
