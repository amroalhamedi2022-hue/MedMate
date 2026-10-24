package com.medmate.personal.brain

import org.json.JSONObject

/** Parse bounded model output with Android's established JSON parser, then verify anchors.
 *  Both local and cloud use this exact acceptance gate. A valid quote is NOT proof of clinical accuracy.
 */
object GeneratedDraftJson {
    fun parseAndValidate(raw: String, evidence: List<Evidence>): GeneratedValidationResult {
        require(raw.length <= 120_000) { "رد النموذج كبير جدًا" }
        val json = try { JSONObject(raw.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("رد AI ليس JSON صالحًا؛ لم تُحفظ أي مسودات")
        }
        val items = json.optJSONArray("items") ?: throw IllegalArgumentException("رد AI لا يحتوي items")
        require(items.length() <= 30) { "رد AI يحتوي عناصر أكثر من المسموح" }
        var invalid = 0
        val candidates = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)
                if (item == null) { invalid++; continue }
                val kind = runCatching { GeneratedKind.valueOf(item.optString("kind")) }.getOrNull()
                if (kind == null) { invalid++; continue }
                val options = item.optJSONArray("options")?.let { array ->
                    (0 until array.length()).map { index -> array.optString(index) }
                }.orEmpty()
                add(GeneratedCandidate(
                    kind = kind, prompt = item.optString("prompt"), answer = item.optString("answer"),
                    options = options, correctIndex = item.optInt("correct_index", -1),
                    explanation = item.optString("explanation"), sourceId = item.optString("source_id"),
                    supportingQuote = item.optString("supporting_quote")
                ))
            }
        }
        val validated = GeneratedStudyDrafts.validate(candidates, evidence)
        return validated.copy(rejected = validated.rejected + invalid)
    }
}
