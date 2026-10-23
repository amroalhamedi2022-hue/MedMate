package dev.ffmpegkit.llama

data class LlamaConfig(val contextSize: Int, val threads: Int)
class FakeModel
class FakeCompletion(val text: String)
object Llama {
    suspend fun loadModel(modelPath: String, config: LlamaConfig): FakeModel {
        require(modelPath.endsWith(".gguf") && config.contextSize >= 1024)
        return FakeModel()
    }
    suspend fun complete(model: FakeModel, prompt: String, systemPrompt: String, maxTokens: Int): FakeCompletion {
        require(prompt.contains("[S1]") && maxTokens in 100..1000)
        return if (prompt.contains("TASK: Produce up to 5")) {
            require(maxTokens == 768 && systemPrompt.contains("JSON"))
            FakeCompletion("""{"items":[{"kind":"FLASHCARD","prompt":"Define this source concept","answer":"Source-based concept","source_id":"S1","supporting_quote":"A source-based concept is described here.","options":[],"correct_index":-1,"explanation":""}]}""")
        } else {
            require(maxTokens == 400)
            FakeCompletion("Source fact [S1]")
        }
    }
    suspend fun releaseModel(model: FakeModel) = Unit
}
