package com.example.arivai.data.quiz

import com.example.arivai.data.model.LlamaEngine
import com.example.arivai.domain.PromptTemplates
import org.json.JSONObject

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class QuizResult(
    val questions: List<QuizQuestion>
)

class QuizGenerator(private val llamaEngine: LlamaEngine) {

    fun generateQuiz(chunkText: String): QuizResult {
        val prompt = PromptTemplates.quizGenerationPrompt(chunkText)
        val rawResponse = llamaEngine.generate(prompt, maxTokens = 600)

        val parsed = parseQuizResponse(rawResponse)
        if (parsed.questions.isNotEmpty()) {
            return parsed
        }

        // Retry once with strict reminder
        val retryPrompt = prompt + "\n\nIMPORTANT: Return ONLY valid JSON."
        val retryResponse = llamaEngine.generate(retryPrompt, maxTokens = 600)
        return parseQuizResponse(retryResponse)
    }

    fun parseQuizResponse(jsonString: String): QuizResult {
        val questions = mutableListOf<QuizQuestion>()
        try {
            val jsonClean = extractJson(jsonString)
            val root = JSONObject(jsonClean)
            val jsonArray = root.getJSONArray("questions")

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val questionText = item.getString("question")
                val optionsArray = item.getJSONArray("options")
                val options = mutableListOf<String>()
                for (j in 0 until optionsArray.length()) {
                    options.add(optionsArray.getString(j))
                }
                val correctIndex = item.getInt("correctIndex")
                val explanation = item.optString("explanation", "See textbook context for details.")

                questions.add(
                    QuizQuestion(
                        question = questionText,
                        options = options,
                        correctIndex = correctIndex,
                        explanation = explanation
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return QuizResult(questions)
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else {
            text
        }
    }
}
