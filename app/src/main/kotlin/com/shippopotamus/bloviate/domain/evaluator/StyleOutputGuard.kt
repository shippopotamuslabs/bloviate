package com.shippopotamus.bloviate.domain.evaluator

/**
 * Output guard for the LLM style pass (ENC-82; SLIMMED by Sean's
 * ENC-100 amendment, 2026-10-19). The guard's contract is now
 * exactly two promises: never insert a refusal, and never insert
 * unrelated text. Three checks:
 *
 * - REFUSAL shape ("I can't help with that...") — inserting one
 *   into the user's text field would be catastrophic, so
 *   refusal-shaped output is a failure, never a result. This
 *   check is unchanged from ENC-82: its false-positive rate is
 *   near zero.
 * - Non-empty — judged by the client, not here: a blank [styled]
 *   passes classification and the caller reports its own
 *   empty-response failure.
 * - Wholesale replacement — if almost none of the source's
 *   content words survive ([MIN_COVERAGE]), the output is not a
 *   restyle of this text at all: it is invented or off-topic text
 *   (the Llama-class failure, ENC-98: generic persona prose at
 *   ~0 coverage). The verdict name stays FIDELITY_LOSS; since
 *   ENC-100 it means wholesale replacement ONLY.
 *
 * What was REMOVED, and why (Sean's reasoning, recorded on
 * ENC-100): the original two-tier fidelity judgment — a hard-token
 * tier (numbers, proper nouns, and the profanity class had to all
 * survive) over a 0.35 soft coverage floor — false-positived
 * against the persona styles, whose entire job is to radically
 * change the text. ENC-91 already had to soften the floor for one
 * faithful Seneca restyle; the ENC-99 build's Gemini completion was
 * most likely guard-killed the same way. A guard false-positive
 * invisibly breaks the feature (the user silently gets the local
 * text), while a dropped word ships VISIBLE text the user can see
 * and fix. The accepted trade: a model silently dropping one
 * word — even a protected-class one — is no longer caught. The
 * baseline pipeline runs before the LLM, so filler removal is
 * already done and is not the guard's concern.
 */
object StyleOutputGuard {

    /** How a styled output fares against its source. */
    enum class Verdict {
        PASS,
        REFUSAL,
        FIDELITY_LOSS,
    }

    /**
     * Classifies [styled] (the cleaned model output) against [source]
     * (the local text that was sent for styling). A blank [styled]
     * passes — emptiness is the client's own failure class.
     */
    fun classify(source: String, styled: String): Verdict {
        if (styled.isBlank()) return Verdict.PASS
        val opening = styled.trim().lowercase().take(OPENING_CHARS)
        if (isRefusal(opening = opening, source = source, styled = styled)) return Verdict.REFUSAL
        if (coverage(source = source, styled = styled) < MIN_COVERAGE) return Verdict.FIDELITY_LOSS
        return Verdict.PASS
    }

    private fun isRefusal(opening: String, source: String, styled: String): Boolean {
        if (CANT_HELP_MARKERS.any { marker -> marker in opening }) return true
        if (REFUSAL_OPENERS.any { pattern -> pattern.containsMatchIn(opening) }) return true
        val apologetic = APOLOGY_OPENER.containsMatchIn(opening)
        if (apologetic && POLICY_MARKERS.any { marker -> marker in opening }) return true
        // Apology-without-output: says sorry and hands back a fraction
        // of the text — a refusal wearing the answer's clothes.
        if (apologetic && wordCount(styled) * 2 < wordCount(source)) return true
        return POLICY_MARKERS.any { marker -> marker in opening } &&
            coverage(source = source, styled = styled) < MIN_COVERAGE
    }

    /** Distinct lowercase word tokens of [text] (apostrophes kept inside words). */
    private fun wordSet(text: String): Set<String> =
        WORD_REGEX.findAll(text.lowercase()).map { match -> match.value }.toSet()

    private fun wordCount(text: String): Int = WORD_REGEX.findAll(text).count()

    /** Share of the source's distinct content words that survive in [styled]. */
    private fun coverage(source: String, styled: String): Double {
        val sourceContent = wordSet(source) - STOPWORDS
        if (sourceContent.isEmpty()) return 1.0
        val styledWords = wordSet(styled)
        val surviving = sourceContent.count { word -> word in styledWords }
        return surviving.toDouble() / sourceContent.size
    }

    private const val OPENING_CHARS: Int = 200

    /**
     * The wholesale-replacement floor (ENC-100 amendment): under a
     * tenth of the source's content words surviving means the
     * output is not a restyle of this text at all — it is invented
     * or off-topic text (the Llama-class shape scores ~0). This is
     * the ONLY coverage judgment left: ENC-82's floor was 0.5,
     * ENC-91 re-based it to 0.35 after it false-positived a
     * faithful heavy Seneca restyle, and ENC-100 slimmed the guard
     * to this floor alone (with the hard-token tier removed
     * entirely) on Sean's call — persona styles substitute
     * diction by design, and catching single dropped words is no
     * longer the guard's job.
     */
    private const val MIN_COVERAGE: Double = 0.10

    private val WORD_REGEX: Regex = Regex("[A-Za-z0-9']+")

    /**
     * The workhorse refusal phrasing, wherever it sits in the opening.
     * "with"/"you" scoped on purpose: the idiom "I can't help but
     * notice..." is legitimate styled text and must not trip this.
     */
    private val CANT_HELP_MARKERS: List<String> = listOf(
        "can't help with", "cannot help with", "can't help you", "cannot help you",
        "can't assist with", "cannot assist with", "can't assist you", "cannot assist you",
        "unable to help with",
    )

    /** Refusal verbs for the "I can't ___" opener (composed below for line length). */
    private const val REFUSAL_VERBS: String =
        "comply|complete|fulfil|fulfill|process|rewrite|rephrase|restyle|paraphrase|generate"

    private val REFUSAL_OPENERS: List<Regex> = listOf(
        // No bare "help"/"assist" verb here: "I can't help but notice..."
        // is an idiom, not a refusal — the help-shaped refusals are
        // caught by CANT_HELP_MARKERS and the unable-to pattern below.
        Regex("""^\W{0,3}i (can't|cannot|can not|won't|will not) (""" + REFUSAL_VERBS + """)\b"""),
        Regex("""^\W{0,3}i (can't|cannot|can not) do (that|this)\b"""),
        Regex("""^\W{0,3}i('m| am) (unable|not able) to (help|assist|comply|rewrite|generate|continue)\b"""),
        Regex("""^\W{0,3}i (must|have to|need to) (decline|refuse)\b"""),
        Regex("""^\W{0,3}i (refuse|decline) to\b"""),
        Regex("""^\W{0,3}as an ai\b"""),
        Regex("""\bagainst my (guidelines|policy|policies|programming)\b"""),
        Regex("""^\W{0,3}i don't have the ability\b"""),
    )

    private val APOLOGY_OPENER: Regex =
        Regex("""^\W{0,3}(i'm sorry|i am sorry|i apologize|i apologise|sorry[,.!])""")

    private val POLICY_MARKERS: List<String> = listOf(
        "content policy",
        "usage policy",
        "my guidelines",
        "my policy",
        "inappropriate content",
        "violates my",
    )

    private val STOPWORDS: Set<String> = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on",
        "for", "with", "as", "at", "by", "from", "into", "over", "under", "about", "after",
        "before", "between", "through", "during", "out", "up", "down", "off", "again", "once",
        "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did", "have",
        "has", "had", "having", "will", "would", "shall", "should", "can", "could", "may",
        "might", "must", "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us",
        "them", "my", "your", "his", "its", "our", "their", "mine", "yours", "ours", "theirs",
        "this", "that", "these", "those", "there", "here", "what", "when", "where", "who",
        "whom", "how", "why", "which", "all", "any", "some", "no", "not", "only", "very",
        "too", "also", "just", "so", "than", "because", "until", "while", "um", "uh", "er",
    )
}
