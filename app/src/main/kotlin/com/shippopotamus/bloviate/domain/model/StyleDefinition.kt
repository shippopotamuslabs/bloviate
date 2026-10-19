package com.shippopotamus.bloviate.domain.model

/** One dictation → styled-output example grounding a style's voice. Both sides are non-blank by construction. */
data class FewShot(
    val raw: String,
    val styled: String,
)

/**
 * How a style is applied. [MECHANICAL] styles are pure on-device string
 * transforms (no model, no network); [LLM] styles need the ENC-73 AI setup
 * and pass through unchanged until it exists.
 */
enum class StyleKind {
    MECHANICAL,
    LLM,
}

/**
 * One writing-style preset (ENC-72): the voice Bloviate shapes dictation
 * into. Definitions are typed Kotlin data in `domain/` — not asset files —
 * so a missing id, a blank instruction, or a malformed pair is a
 * compile-time or unit-test failure, never a runtime parse surprise.
 */
data class StyleDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val sample: String,
    val systemInstructions: String,
    val fewShots: List<FewShot>,
    val kind: StyleKind,
)

/**
 * The full style catalog (ENC-72). [DEFAULT] is `normal-caps-casual` —
 * today's baseline behavior. [byId] resolves any id, falling back to the
 * default for unknown ids (e.g. a style removed after being selected).
 */
object StyleCatalog {

    private const val NEVER_CLAUSE: String =
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth."

    private val LowercaseCasual: StyleDefinition = StyleDefinition(
        id = "lowercase-casual",
        displayName = "lowercase casual",
        description = "all lowercase, relaxed and off-the-cuff.",
        sample = "yeah so i was thinking we could just go tomorrow",
        systemInstructions = "Casing: every letter lowercase, always — no sentence capitals, " +
            "no capitalised proper nouns. Punctuation: relaxed — drop exactly one trailing period " +
            "at the very end if present, keep ? and ! and everything else untouched. " +
            "Paragraphing: one flowing block, no extra line breaks. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "Hey I was thinking we could grab lunch tomorrow.",
                styled = "hey i was thinking we could grab lunch tomorrow",
            ),
            FewShot(
                raw = "Can you believe it? The meeting is at noon!",
                styled = "can you believe it? the meeting is at noon!",
            ),
        ),
        kind = StyleKind.MECHANICAL,
    )

    private val NormalCapsCasual: StyleDefinition = StyleDefinition(
        id = "normal-caps-casual",
        displayName = "Normal Caps Casual",
        description = "Just how you said it, cleaned up.",
        sample = "Yeah, so I was thinking we could just go tomorrow.",
        systemInstructions = "Casing: normal sentence case — capitalise the first word " +
            "and proper nouns, nothing more. Punctuation: keep the speaker's end marks exactly " +
            "as transcribed. Paragraphing: a single paragraph per utterance. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "hey i was thinking we could grab lunch tomorrow",
                styled = "Hey I was thinking we could grab lunch tomorrow.",
            ),
            FewShot(
                raw = "the meeting is at noon bring the reports",
                styled = "The meeting is at noon, bring the reports.",
            ),
        ),
        kind = StyleKind.MECHANICAL,
    )

    private val Professional: StyleDefinition = StyleDefinition(
        id = "professional",
        displayName = "Professional",
        description = "Crisp, polished, ready to send.",
        sample = "Following up on our discussion, I propose we meet tomorrow.",
        systemInstructions = "Casing: standard sentence case with every proper noun " +
            "capitalised. Punctuation: full formal punctuation — one idea per sentence, " +
            "commas where a professional would put them. Paragraphing: short paragraphs " +
            "with one blank line between them. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "yeah so like following up on our chat i think we should meet tomorrow",
                styled = "Following up on our discussion, I believe we should meet tomorrow.",
            ),
            FewShot(
                raw = "sorry for the late reply the report will be done friday",
                styled = "Apologies for the delayed reply. The report will be ready on Friday.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Edgelord: StyleDefinition = StyleDefinition(
        id = "edgelord",
        displayName = "Edgelord",
        description = "Chaotic, unhinged, extremely online.",
        sample = "went to the store. bought milk. absolutely feral behaviour tbh",
        systemInstructions = "Casing: chaotic — mostly lowercase with the occasional word " +
            "in FULL SCREAMING CAPS for emphasis. Punctuation: aggressive — fragments, " +
            "abrupt full stops, zero chill. Paragraphing: staccato one-liners, each on " +
            "its own line. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "I went to the store and bought some milk.",
                styled = "went to the store. bought milk. ABSOLUTELY feral behaviour tbh",
            ),
            FewShot(
                raw = "The meeting has been moved to three o clock.",
                styled = "meeting moved to 3. LOVE that for me. anyway",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Genius: StyleDefinition = StyleDefinition(
        id = "genius",
        displayName = "Genius",
        description = "Precise, erudite, faintly condescending.",
        sample = "The proposition, while superficially plausible, collapses under scrutiny.",
        systemInstructions = "Casing: precise academic sentence case. Punctuation: semicolons " +
            "and em dashes welcome; every clause exactly placed. Paragraphing: dense, " +
            "well-structured paragraphs that build an argument. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i think this idea is pretty good but it might not work",
                styled = "I grant the proposal has merit; whether it survives contact with reality " +
                    "is another question.",
            ),
            FewShot(
                raw = "we should test it first before deciding",
                styled = "Prudence demands empirical validation before commitment.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Aristotle: StyleDefinition = StyleDefinition(
        id = "aristotle",
        displayName = "Aristotle",
        description = "Syllogisms, virtues, and first principles.",
        sample = "All meetings that could be messages are wastes; this was a message.",
        systemInstructions = "Casing: classical sentence case, formal throughout. " +
            "Punctuation: measured, periodic sentences — premises, then conclusion. " +
            "Paragraphing: reason first in full paragraphs, concluding maxim last. " +
            NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "we should leave early because traffic will be bad",
                styled = "All travellers who depart late are ensnared by traffic; we are travellers. " +
                    "Therefore we depart early.",
            ),
            FewShot(
                raw = "he exercises every day and he is healthy",
                styled = "Exercise is the habit of a flourishing body; he exercises daily. " +
                    "Hence his health — virtue made visible.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Seneca: StyleDefinition = StyleDefinition(
        id = "seneca",
        displayName = "Seneca",
        description = "Stoic letters on time, anger, and fortune.",
        sample = "I act as though I will live forever; I never read the terms.",
        systemInstructions = "Casing: plain sentence case, as in a private notebook. " +
            "Punctuation: short moral sentences, rhetorical questions sparingly. " +
            "Paragraphing: brief paragraphs — the matter itself, the reflection on it, " +
            "a closing maxim. " +
            NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i am stressed about work and i have no time",
                styled = "I say I have no time, yet I give my hours away to worry. " +
                    "The present is the only possession no fortune can seize; I will guard it.",
            ),
            FewShot(
                raw = "my flight was delayed and i am angry",
                styled = "The delay was not in my power; my anger is. " +
                    "I will not hand fortune a second victory.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val MarcusAurelius: StyleDefinition = StyleDefinition(
        id = "marcus-aurelius",
        displayName = "Marcus Aurelius",
        description = "Meditations to yourself, numbered and spare.",
        sample = "Begin. The obstacle is the work; do the work.",
        systemInstructions = "Casing: spare sentence case, imperatives to the self. " +
            "Punctuation: terse — short sentences, fragments permitted for emphasis. " +
            "Paragraphing: numbered meditations, each standing alone. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i need to focus and stop getting distracted by my phone",
                styled = "1. The phone will wait; the hour will not. 2. Return to the work. " +
                    "Return again. That is the whole discipline.",
            ),
            FewShot(
                raw = "someone was rude to me today and i keep thinking about it",
                styled = "1. Their rudeness belongs to them; my rehearsal of it belongs to me. " +
                    "2. Drop it. The present needs me whole.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val OscarWilde: StyleDefinition = StyleDefinition(
        id = "oscar-wilde",
        displayName = "Oscar Wilde",
        description = "Epigrams, paradoxes, and devastating politeness.",
        sample = "To miss one meeting is a misfortune; to miss two looks like carelessness.",
        systemInstructions = "Casing: elegant sentence case, every paradox polished. " +
            "Punctuation: balanced, musical — antitheses joined by semicolons and colons. " +
            "Paragraphing: a glittering paragraph or two, closing on an epigram. " +
            NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i am too busy to go to parties anymore",
                styled = "To be busy is the sincerest form of disappearing; " +
                    "I find I am becoming remarkably sincere.",
            ),
            FewShot(
                raw = "people always give advice they never follow",
                styled = "Nothing is so liberally given as advice, and nothing so rarely taken — " +
                    "particularly by the giver.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Talkie: StyleDefinition = StyleDefinition(
        id = "talkie",
        displayName = "Talkie",
        description = "Bright, clipped 1920s newspaper English.",
        sample = "Wire just in. The vote is Thursday. I am for it, and so is the town.",
        systemInstructions = "Casing: crisp 1920s newspaper sentence case — capitals where a " +
            "quality daily of 1925 would put them, no more. Punctuation: plain — full stops " +
            "do the work, commas are rationed, and the semicolon is left to the other fellow. " +
            "Sentence architecture: CLIP, never swell. Every sentence is short, declarative, " +
            "and finished before it can gather finery — no sentence runs past twelve words, " +
            "facts stand one to a line in a wire-service row, and NEVER write a periodic " +
            "sentence that holds its meaning back for a grand arrival; that swell belongs " +
            "to the veranda, not the newsroom. Paragraphing: one brisk paragraph, no " +
            "lingering. Register: educated 1920s American newspaper prose — bright, " +
            "assured, plain-spoken, the slang of the press room rather than the salon, " +
            "period idiom ('rather', 'quite', 'splendid') sprinkled, never poured. " +
            "Render the 1920s register of the transcript's own language — restyle the text " +
            "in its own tongue in that language's period voice; never translate it into " +
            "English. Period flavor touches FORM ONLY — diction, rhythm, idiom: a modern " +
            "thing keeps the speaker's name for it (my phone stays my phone, never a period " +
            "apparatus), and no period slur or item of bigotry the speaker did not use may " +
            "ever appear. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i took some photos of the kids at the park with my new phone " +
                    "and the battery lasted all day",
                styled = "I took photos of the kids at the park. My new phone did the work. " +
                    "The battery lasted all day.",
            ),
            FewShot(
                raw = "the backup script failed at step three, the database migration " +
                    "filled the disk and we lost two hours of logs",
                styled = "The backup script failed at the third step. " +
                    "The database migration filled the disk. Two hours of logs are gone.",
            ),
            FewShot(
                raw = "i disagree with the plan, the cost is too high " +
                    "and the timeline is unrealistic",
                styled = "I disagree with the plan. The cost is too high. " +
                    "The timeline is pure fantasy.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    /** The style an absent or unrecognized selection reads as: today's baseline behavior. */
    private val TheColonel: StyleDefinition = StyleDefinition(
        id = "the-colonel",
        displayName = "The Colonel",
        description = "Courtly, periodic, unhurried — the old Southern written voice.",
        sample = "I am most grateful for the news, and I shall attend to the matter in due course.",
        systemInstructions = "Casing: formal sentence case throughout, nothing clipped or " +
            "casual. Punctuation: the semicolon and the colon do the joinery; commas fall " +
            "where the cadence breathes. Sentence architecture: DILATE, never clip. Every " +
            "sentence is built as a period — subordinate clauses first, balanced against " +
            "one another, courtesy formulae woven through, the main clause arriving only " +
            "at the end with its full weight; NEVER clip a thought into wire-service " +
            "fragments or let a fact stand bare and unattended — that haste belongs to " +
            "the newsroom, not the veranda. Paragraphing: one measured paragraph, " +
            "unhurried from the first word to the last. Register: the educated " +
            "nineteenth-century Southern written voice — elaborate courtesy, Latinate " +
            "diction with a scriptural cadence beneath it, gracious formality even in " +
            "disagreement; courtly without servility, assured and never hurried. It is " +
            "a voice, not a letter: no salutations, no sign-offs, no framing of address. " +
            "Render this register of the transcript's own language — restyle the text in " +
            "its own tongue in that language's period voice; never translate it into " +
            "English. The period lends its syntax, diction, and courtesy ONLY: facts, " +
            "names, numbers, and referents never change, a modern thing keeps the " +
            "speaker's name for it, and the style must never import the era's racial " +
            "vocabulary or Lost Cause apologetics, on any input, ever. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i took some photos of the kids at the park with my new phone " +
                    "and the battery lasted all day",
                styled = "I took the children's photographs at the park with my new phone, " +
                    "and the battery, I am gratified to say, served faithfully the whole " +
                    "of the day.",
            ),
            FewShot(
                raw = "the backup script failed at step three, the database migration " +
                    "filled the disk and we lost two hours of logs",
                styled = "The backup script failed at its third step, the database migration " +
                    "having filled the disk beyond its capacity, and we are the poorer " +
                    "by two hours of logs.",
            ),
            FewShot(
                raw = "i disagree with the plan, the cost is too high " +
                    "and the timeline is unrealistic",
                styled = "With respect, I cannot commend the plan: its cost exceeds what " +
                    "prudence allows, and its schedule promises what no honest effort " +
                    "can deliver.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    /** The style an absent or unrecognized selection reads as: today's baseline behavior. */

    val DEFAULT: StyleDefinition = NormalCapsCasual

    /** Every style Bloviate offers, in display order. */
    val ALL: List<StyleDefinition> = listOf(
        LowercaseCasual,
        NormalCapsCasual,
        Professional,
        Edgelord,
        Genius,
        Aristotle,
        Seneca,
        MarcusAurelius,
        OscarWilde,
        Talkie,
        TheColonel,
    )

    /** Looks up [id]; unknown ids resolve to [DEFAULT], never null. */
    fun byId(id: String): StyleDefinition = ALL.firstOrNull { style -> style.id == id } ?: DEFAULT
}
