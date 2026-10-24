package com.medmate.personal.brain

/** Keep a generated draft's verified source quote with the saved item, without a DB migration.
 *  The quote is evidence to REVIEW, not proof that the model's medical claim is correct.
 */
object StudyEvidenceNote {
    fun withSource(explanation: String, verifiedQuote: String): String {
        val quote = verifiedQuote.trim()
        require(quote.length in 16..500) { "لا يوجد اقتباس مصدر موثّق" }
        val text = listOfNotNull(explanation.trim().takeIf { it.isNotBlank() },
            "📖 اقتباس للتحقق من الصفحة (ليس تصديقًا طبيًا آليًا):\n$quote").joinToString("\n\n")
        require(text.length <= 6000) { "الشرح واقتباس المصدر أطول من الحد المسموح" }
        return text
    }
}
