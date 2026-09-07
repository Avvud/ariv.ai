package com.example.arivai.domain

object PromptTemplates {

    private const val SYSTEM_PROMPT = "You are MathMate, a patient Socratic AI tutor."

    // Qwen3 (and Qwen2.5) are trained almost entirely around this ChatML-style
    // format. Feeding raw, unwrapped text (as before) makes the model unsure
    // of its role and produces weaker, less on-task output.
    //
    // Qwen3 also has "thinking mode" ON by default: without being told
    // otherwise, it first generates an internal <think>...</think> reasoning
    // block before the real answer. For a short Socratic hint that's wasted
    // latency (burns the maxTokens budget on invisible reasoning) and can
    // leak raw <think> text into the chat bubble. Appending "/no_think" AND
    // pre-filling an empty, already-closed <think></think> block are both
    // documented ways to force the model straight to its final answer -
    // using both together is the most reliable combination.
    private fun wrapChatTemplate(userContent: String, systemPrompt: String = SYSTEM_PROMPT): String {
        return "<|im_start|>system\n$systemPrompt<|im_end|>\n" +
                "<|im_start|>user\n$userContent<|im_end|>\n" +
                "<|im_start|>assistant\n"
    }

    fun socraticPrompt(
        retrievedChunks: String,
        conversationHistory: String,
        userMessage: String
    ): String {
        val instructions = """
You NEVER give the final answer directly.
Use ONLY the provided textbook context to ground your explanation — if the context doesn't cover it, say so honestly.
Respond with ONE guiding question or hint that helps the student take the next step themselves.
Keep it under 3 sentences. Do not solve the full problem.

CONTEXT:
$retrievedChunks

CONVERSATION SO FAR:
$conversationHistory

STUDENT'S MESSAGE:
$userMessage
        """.trimIndent()
        return wrapChatTemplate(instructions)
    }

    fun revealAnswerPrompt(
        retrievedChunks: String,
        insistCount: Int,
        userMessage: String
    ): String {
        val instructions = """
The student has genuinely attempted this problem and explicitly asked for the answer $insistCount times.
You may now give the full worked answer, using the textbook context below, with a brief explanation of each step.

CONTEXT:
$retrievedChunks

STUDENT'S MESSAGE:
$userMessage
        """.trimIndent()
        return wrapChatTemplate(instructions)
    }

    fun quizGenerationPrompt(chunkText: String): String {
        val instructions = """
Based ONLY on the following textbook excerpt, write exactly 3 multiple-choice questions testing understanding of the material.
Respond with ONLY valid JSON, no other text, in this exact schema:
{
  "questions": [
    {"question": "...", "options": ["A", "B", "C", "D"], "correctIndex": 0, "explanation": "..."}
  ]
}

EXCERPT:
$chunkText
        """.trimIndent()
        return wrapChatTemplate(instructions)
    }
}