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
    private val evidenceSeal: String? = null,
    private val createdAtNanos: Long,
    private val clockNanos: () -> Long
) {
    private fun expired(): Boolean = clockNanos() - createdAtNanos >= GRANT_TTL_MILLIS * 1_000_000L
    // One in-memory capability must never authorize two outbound requests.
    // Atomic claiming also covers concurrent callers retaining the same grant.
    private var consumed = false

    @Synchronized
    fun permits(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean =
        !consumed && !expired() && matches(endpointInput, modelInput, current)

    @Synchronized
    fun consume(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean {
        if (consumed || expired() || !matches(endpointInput, modelInput, current)) return false
        consumed = true
        return true
    }

    /** The outbound path only accepts a consent sealed to these exact excerpts.
     * Legacy unsealed grants cannot authorize a cloud request. */
    @Synchronized
    fun permits(endpointInput: String, modelInput: String, current: BrainRequestIdentity, seal: String): Boolean =
        !consumed && !expired() && evidenceSeal != null && evidenceSeal == seal &&
            matches(endpointInput, modelInput, current)

    @Synchronized
    fun consume(endpointInput: String, modelInput: String, current: BrainRequestIdentity, seal: String): Boolean {
        if (consumed || expired() || evidenceSeal == null || evidenceSeal != seal ||
            !matches(endpointInput, modelInput, current)) return false
        consumed = true
        return true
    }

    private fun matches(endpointInput: String, modelInput: String, current: BrainRequestIdentity): Boolean =
        runCatching { GatewayPolicy.endpoint(endpointInput) }.getOrNull() == endpoint &&
            modelInput.trim() == model && current == request

    companion object {
        internal const val GRANT_TTL_MILLIS = 5 * 60 * 1000L

        fun grant(endpointInput: String, modelInput: String, request: BrainRequestIdentity): CloudTransferConsent =
            grantInternal(endpointInput, modelInput, request, null) { System.nanoTime() }

        fun grant(endpointInput: String, modelInput: String, request: BrainRequestIdentity,
                  evidenceSeal: String): CloudTransferConsent {
            require(evidenceSeal.matches(Regex("[a-f0-9]{64}"))) { "Invalid PDF evidence seal" }
            return grantInternal(endpointInput, modelInput, request, evidenceSeal) { System.nanoTime() }
        }

        /** Deterministic monotonic clock seam for expiry regression tests only. */
        internal fun grantForTest(endpointInput: String, modelInput: String, request: BrainRequestIdentity,
                                  evidenceSeal: String, clockNanos: () -> Long): CloudTransferConsent {
            require(evidenceSeal.matches(Regex("[a-f0-9]{64}"))) { "Invalid PDF evidence seal" }
            return grantInternal(endpointInput, modelInput, request, evidenceSeal, clockNanos)
        }

        private fun grantInternal(endpointInput: String, modelInput: String, request: BrainRequestIdentity,
                                  evidenceSeal: String?, clockNanos: () -> Long): CloudTransferConsent {
            val endpoint = GatewayPolicy.endpoint(endpointInput)
            val model = modelInput.trim()
            require(model.length in 1..96 && model.all { it.isLetterOrDigit() || it in "._-/:" }) {
                "اسم النموذج غير صالح"
            }
            require(request.chapterSha256.isNotBlank() && request.question.isNotBlank()) {
                "لا يمكن إرسال طلب بدون سؤال ومصدر محددين"
            }
            val createdAtNanos = clockNanos()
            return CloudTransferConsent(endpoint, model, request, evidenceSeal, createdAtNanos, clockNanos)
        }
    }
}
