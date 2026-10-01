package com.karen.assistant

object PhraseAliases {
    fun resolve(commands: Map<String, String>, phrase: String): String? {
        val normalized = IntentParser.normalize(phrase)
        commands[normalized]?.let { return it }
        val cleaned = normalized.replace(Regex("\\b(карен|пожалуйста|плиз)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        return commands[cleaned]
    }
    fun best(alternatives: List<String>, commands: Map<String, String>): String? {
        val first = alternatives.firstOrNull() ?: return null
        if (resolve(commands, first) != null || IntentParser.parse(first).kind != CommandKind.UNKNOWN) return first
        return alternatives.firstOrNull { resolve(commands, it) != null || IntentParser.parse(it).kind !in setOf(CommandKind.UNKNOWN, CommandKind.QUESTION) } ?: first
    }
}
