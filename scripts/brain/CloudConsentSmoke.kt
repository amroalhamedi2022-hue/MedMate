package com.medmate.personal.brain

fun main() {
    var checks = 0
    fun checkCase(value: Boolean, name: String) {
        check(value) { name }
        checks++
    }
    val source = BrainRequestIdentity("chapter-abc", "Explain osteomyelitis", true, 3)
    val grant = CloudTransferConsent.grant(
        "https://example.org/v1/chat/completions", "local-model", source
    )
    checkCase(grant.permits("https://example.org/v1/chat/completions", "local-model", source), "approved")
    checkCase(!grant.permits("https://evil.example/v1/chat/completions", "local-model", source), "other host")
    checkCase(!grant.permits("http://example.org/v1/chat/completions", "local-model", source), "cleartext")
    checkCase(!grant.permits("https://example.org/v1/chat/completions", "other-model", source), "other model")
    checkCase(!grant.permits("https://example.org/v1/chat/completions", "local-model", source.copy(question = "new")), "other question")
    checkCase(!grant.permits("https://example.org/v1/chat/completions", "local-model", source.copy(chapterSha256 = "other")), "other chapter")
    checkCase(!grant.permits("https://example.org/v1/chat/completions", "local-model", source.copy(sourcePage = 4)), "other page")
    checkCase(runCatching { CloudTransferConsent.grant("https://example.org/v1/chat/completions", "", source) }.isFailure, "empty model")
    checkCase(runCatching { CloudTransferConsent.grant("https://example.org/v1/chat/completions", "ok", source.copy(question = "")) }.isFailure, "empty question")
    checkCase(runCatching { CloudTransferConsent.grant("https://example.org/v1/chat/completions?token=leak", "ok", source) }.isFailure, "URL query")
    checkCase(runCatching { CloudTransferConsent.grant("http://example.org/v1/chat/completions", "ok", source) }.isFailure, "HTTP endpoint")
    println("PASS CloudTransferConsent: $checks pure Kotlin checks")
}
