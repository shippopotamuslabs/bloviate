package com.shippopotamus.bloviate.domain.evaluator

/**
 * Output guard for the LLM style pass (ENC-82). The style layer's
 * contract is that voice may change but words must not: facts, names,
 * numbers, and profanity survive a restyle. Two model failure shapes
 * violate that contract while looking like success:
 *
 * - a REFUSAL ("I can't help with that...") — inserting one into the
 *   user's text field would be catastrophic, so refusal-shaped output
 *   is a failure, never a result;
 * - a silent FIDELITY loss — the 2026-10-19 dogfood datapoint: a 200
 *   completion that restyled everything except one content word (a
 *   slur) it quietly dropped. No refusal, no error — altered words
 *   shipped under the user's name.
 *
 * Both verdicts are failures routed into the client's fallback chain,
 * exactly like a transport error. The guard errs toward PASS: a false
 * refusal/fidelity verdict costs one fallback to the next model (and
 * at worst the local text — the pre-LLM behavior), while a false PASS
 * can put words the user never said into their field.
 *
 * Fidelity is judged in two tiers. HARD tokens must all survive
 * (case-insensitively): numbers, proper nouns, and the profanity class
 * models are observed to silently censor — ordinary vocabulary is the
 * style's legitimate playground (persona styles substitute diction by
 * design). Over the rest, a soft floor: if under a third of the
 * source's distinct content words survive, the output is not a
 * restyle of this text at all. The baseline pipeline runs before the
 * LLM, so filler removal is already done and is not the guard's
 * concern.
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
        if (hardTokens(source).any { token -> token !in wordSet(styled) }) return Verdict.FIDELITY_LOSS
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

    /**
     * The tokens a restyle may never drop: numbers, proper nouns
     * (capitalized mid-sentence, or all-caps), and the profanity class.
     */
    private fun hardTokens(source: String): Set<String> {
        val matches = WORD_REGEX.findAll(source).toList()
        val hard = mutableSetOf<String>()
        matches.forEachIndexed { index, match ->
            val raw = match.value
            val lowered = raw.lowercase()
            when {
                raw.any { char -> char.isDigit() } -> hard += lowered
                lowered in PROFANITY -> hard += lowered
                isProperNoun(source = source, matches = matches, index = index) -> hard += lowered
            }
        }
        return hard
    }

    private fun isProperNoun(source: String, matches: List<MatchResult>, index: Int): Boolean {
        val raw = matches[index].value
        if (raw.length < 2 || !raw[0].isUpperCase()) return false
        if (raw.drop(1).all { char -> char.isUpperCase() || !char.isLetter() }) return true
        if (index == 0) return false
        // A capitalized word is a name unless grammar put it there:
        // sentence-initial words are capitalized for free.
        val gap = source.substring(matches[index - 1].range.last + 1, matches[index].range.first)
        return gap.none { char -> char == '.' || char == '!' || char == '?' || char == '\n' }
    }

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
     * Under a third of the content surviving is not a restyle of this
     * text. ENC-91 re-based this from 0.5: persona styles substitute
     * ordinary diction by design, and on SHORT dictations a handful of
     * legitimate substitutions (checking → reach for, feel bad →
     * ashamed) pushed faithful restyles under the half line. The hard
     * tier remains the real censor-catcher — the Idiocracy drop fails
     * there regardless of coverage — and the wholesale-replacement
     * shape still scores ~0.
     */
    private const val MIN_COVERAGE: Double = 0.35

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

    /** The profanity class models are observed to silently drop (ENC-82 dogfood). */
    private val PROFANITY: Set<String> = setOf(
        "fuck", "fucked", "fucking", "fucker", "shit", "bitch", "bitches", "cunt",
        "dick", "cock", "pussy", "whore", "slut", "fag", "fags", "faggot",
        "nigger", "nigga", "asshole", "bastard", "bastards",
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
