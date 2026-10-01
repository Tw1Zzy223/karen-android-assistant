package com.karen.assistant

object AppNames {
    private fun latin(text: String): String {
        val letters = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ы' to "y", 'ь' to "", 'ъ' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")
        return text.map { letters[it] ?: it.toString() }.joinToString("")
    }
    private val aliases = mapOf("телеграм" to "telegram", "телеграмм" to "telegram", "телега" to "telegram", "ютуб" to "youtube", "ю туб" to "youtube", "ватсап" to "whatsapp", "вацап" to "whatsapp", "вотсап" to "whatsapp", "хром" to "chrome", "дискорд" to "discord", "тик ток" to "tiktok", "тик-ток" to "tiktok", "вк" to "vk")
    fun canonical(raw: String): String {
        val text = IntentParser.normalize(raw)
        aliases[text]?.let { return it }
        val similar = aliases.keys.firstOrNull { it.length >= 5 && text.length >= 5 && IntentParser.distance(text, it) <= 1 }
        return similar?.let { aliases[it] } ?: text
    }
    fun score(query: String, name: String, packageName: String): Double {
        val q = canonical(query)
        val label = canonical(name)
        if (q.isBlank()) return 1.0
        if (q == label || q == packageName) return 0.0
        if (label.startsWith("$q ") || label.endsWith(" $q")) return 0.08
        if (q.length >= 3 && (label.contains(q) || packageName.split('.').any { it == q })) return 0.12
        val direct = IntentParser.distance(q, label).toDouble() / maxOf(q.length, label.length).coerceAtLeast(1)
        val phonetic = IntentParser.distance(latin(q), latin(label)).toDouble() / maxOf(latin(q).length, latin(label).length).coerceAtLeast(1)
        return minOf(direct, phonetic)
    }
}
