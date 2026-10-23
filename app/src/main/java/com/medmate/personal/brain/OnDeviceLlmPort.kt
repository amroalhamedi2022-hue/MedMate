package com.medmate.personal.brain

import java.io.File

/** Native LLM implementation is intentionally NOT bundled in this preview. */
interface OnDeviceLlmPort {
    val loadedModelName: String?
    suspend fun loadModel(file: File)
    suspend fun generate(prepared: PreparedPrompt, onToken: (String) -> Unit): String
    suspend fun unload()
}

/** No silent substitute with fake AI output. Wiring target: official llama.cpp Android binding. */
class UnavailableOnDeviceLlm : OnDeviceLlmPort {
    override val loadedModelName: String? = null
    override suspend fun loadModel(file: File): Unit = throw UnsupportedOperationException("On-device LLM not yet integrated")
    override suspend fun generate(prepared: PreparedPrompt, onToken: (String) -> Unit): String =
        throw UnsupportedOperationException("On-device LLM not yet integrated")
    override suspend fun unload() = Unit
}
