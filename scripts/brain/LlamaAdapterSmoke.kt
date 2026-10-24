import com.medmate.personal.brain.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

fun main() = runBlocking {
    val fakeFile = Files.createTempFile("medmate-model-", ".gguf").toFile()
    try {
        fakeFile.writeBytes(ByteArray(256).apply {
            "GGUF".toByteArray().copyInto(this)
            this[4] = 3
        })
        val port = LlamaAndroidPort()
        check(port.loadedModelName == null)
        port.loadModel(fakeFile)
        check(port.loadedModelName == fakeFile.name)
        val evidence = listOf(Evidence("c:0:0", "c", 0, "A source-based concept is described here."))
        val prepared = MedicalPromptBuilder.prepare(GroundedRequest("c", "concept", evidence, 1200))
        var callback = ""
        val answer = port.generate(prepared) { callback = it }
        check(answer == callback && answer.contains("[S1]"))
        val structured = port.generate(StudyDraftPrompt.prepare(prepared)) { }
        check(structured.contains("supporting_quote") && structured.contains("items"))
        port.unload()
        check(port.loadedModelName == null)
        val attempt = runCatching { port.generate(prepared) { } }
        check(attempt.isFailure)
        println("Llama adapter fake-backend explanation + JSON draft + lifecycle smoke passed")
    } finally { fakeFile.delete() }
}
