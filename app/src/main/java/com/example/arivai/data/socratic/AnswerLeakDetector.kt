package com.example.arivai.data.socratic

class AnswerLeakDetector {

    private val directAnswerIndicators = listOf(
        Regex("(?i)\\b(the\\s+final\\s+answer\\s+is|the\\s+answer\\s+is|=|so\\s+x\\s*=)\\b"),
        Regex("(?i)\\b(the\\s+solution\\s+is|therefore\\s+the\\s+answer|correct\\s+answer\\s+is)\\b"),
        Regex("(?i)\\b(answer\\s*:\\s*\\d+)\\b")
    )

    fun containsDirectAnswer(response: String): Boolean {
        return directAnswerIndicators.any { it.containsMatchIn(response) }
    }

    fun sanitizeOrFilterHint(response: String, isInsistingOrThresholdReached: Boolean): String {
        if (isInsistingOrThresholdReached) {
            return response
        }

        if (containsDirectAnswer(response)) {
            // Replace direct answer with a Socratic guidance string
            var sanitized = response
            for (pattern in directAnswerIndicators) {
                val match = pattern.find(sanitized)
                if (match != null) {
                    val cutIndex = match.range.first
                    if (cutIndex > 20) {
                        sanitized = sanitized.substring(0, cutIndex) + "... What do you think is the next step to solve this?"
                    } else {
                        return "Here is a hint: Try breaking down the problem step-by-step using the formulas in your textbook. What is given in the question?"
                    }
                }
            }
            return sanitized
        }

        return response
    }
}
