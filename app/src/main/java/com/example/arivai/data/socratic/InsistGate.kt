package com.example.arivai.data.socratic

class InsistGate {
    private val insistCounts = mutableMapOf<String, Int>()

    private val insistPatterns = listOf(
        Regex("(?i)\\b(just\\s+tell\\s+me|give\\s+me\\s+the\\s+answer|tell\\s+me\\s+the\\s+answer)\\b"),
        Regex("(?i)\\b(i\\s+give\\s+up|what\\s+is\\s+the\\s+answer|show\\s+me\\s+the\\s+solution)\\b"),
        Regex("(?i)\\b(give\\s+up|answer\\s+please|just\\s+give\\s+the\\s+answer|solution\\s+please)\\b"),
        Regex("(?i)\\b(directly\\s+tell|direct\\s+answer|stop\\s+hinting|don't\\s+hint)\\b")
    )

    fun registerInsistAttempt(questionId: String): Int {
        val currentCount = insistCounts.getOrDefault(questionId, 0) + 1
        insistCounts[questionId] = currentCount
        return currentCount
    }

    fun getInsistCount(questionId: String): Int {
        return insistCounts.getOrDefault(questionId, 0)
    }

    fun shouldRevealAnswer(questionId: String, threshold: Int = 3): Boolean {
        return getInsistCount(questionId) >= threshold
    }

    fun isInsistPhrase(userMessage: String): Boolean {
        return insistPatterns.any { pattern -> pattern.containsMatchIn(userMessage) }
    }

    fun reset(questionId: String) {
        insistCounts.remove(questionId)
    }

    fun resetAll() {
        insistCounts.clear()
    }
}
