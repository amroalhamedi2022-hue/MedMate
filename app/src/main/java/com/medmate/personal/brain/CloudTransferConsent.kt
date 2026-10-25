package com.medmate.personal.brain

/**
 * One explicit approval for a single outbound PDF-excerpt request.
 * The grant is bound to the canonical HTTPS destination, model and exact
 * indexed question/chapter/page context. Never persist this object or tokens.
 *
 * This is a client-side consent boundary, not a substitute for TLS verification
 * or a medical review of the generated answer.
 */
class CloudTransferConsent private constructor(
    val endpoint: String,
    val model: String,
    val request: BrainRequestIdentity,
    private val evidenceSeal: String? = null
) {
    // One in-memory capability must never authorize two outbound requests.
    // Atomic claiming also covers concurrent callers retaining the same grant.
    private var consumed = false

    @Synchronized
    fun permits(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean =
        !consumed && matches(endpointInput, modelInput, current)

    @Synchronized
    fun consume(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean {
        if (consumed || !matches(endpointInput, modelInput, current)) return false
        consumed = true
        return true
    }

    /** The outbound path only accepts a consent sealed to these exact excerpts.
     * Legacy unsealed grants cannot authorize a cloud request. */
    @Synchronized
    fun permits(endpointInput: String, modelInput: String, current: BrainRequestIdentity, seal: String): Boolean =
        !consumed && evidenceSeal != null && evidenceSeal == seal &&
            matches(endpointInput, modelInput, current)

    @Synchronized
    fun consume(endpointInput: String, modelInput: String, current: BrainRequestIdentity, seal: String): Boolean {
        if (consumed || evidenceSeal == null || evidenceSeal != seal ||
            !matches(endpointInput, modelInput, current)) return false
        consumed = true
        return true
    }

    private fun matches(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean =
        runCatching { GatewayPolicy.endpoint(endpointInput) }.getOrNull() == endpoint &&
            modelInput.trim() == model && current == request

    companion object {
        fun grant(endpointInput: String, modelInput: String, request: BrainRequestIdentity): CloudTransferConsent =
            grantInternal(endpointInput, modelInput, request, null)

        fun grant(endpointInput: String, modelInput: String, request: BrainRequestIdentity,
                  evidenceSeal: String): CloudTransferConsent {
            require(evidenceSeal.matches(Regex("[a-f0-9]{64}"))) { "Invalid PDF evidence seal" }
            return grantInternal(endpointInput, modelInput, request, evidenceSeal)
        }

        private fun grantInternal(endpointInput: String, modelInput: String, request: BrainRequestIdentity,
                                  evidenceSeal: String?): CloudTransferConsent {
            val endpoint = GatewayPolicy.endpoint(endpointInput)
            val model = modelInput.trim()
            require(model.length in 1..96 && model.all { it.isLetterOrDigit() || it in "._-/:" }) {
                "اسم النموذج غير صالح"
            }
            require(request.chapterSha256.isNotBlank() && request.question.isNotBlank()) {
                "لا يمكن إرسال طلب بدون سؤال ومصدر محددين"
            }
            return CloudTransferConsent(endpoint, model, request, evidenceSeal)
        }
    }
}
