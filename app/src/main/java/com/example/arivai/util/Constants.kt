package com.example.arivai.util

object Constants {
    const val DEFAULT_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val DEFAULT_EMBEDDING_MODEL_URL = "https://huggingface.co/optimum/all-MiniLM-L6-v2/resolve/main/model.onnx"

    const val MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val CUSTOM_MODEL_FILENAME = "selected_model.gguf"
    const val EMBEDDING_MODEL_FILENAME = "all-MiniLM-L6-v2.onnx"
    const val VECTOR_INDEX_FILENAME = "vector_index.json"

    const val INSIST_THRESHOLD = 3
    const val CHUNK_SIZE_WORDS = 350
    const val CHUNK_OVERLAP_WORDS = 40
}
