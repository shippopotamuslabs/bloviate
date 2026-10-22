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
        description = "Definitions, distinctions, and first causes.",
        sample = "Let us distinguish: a meeting deliberates; a message informs. This was a message.",
        systemInstructions = "Casing: formal sentence case, impersonal throughout. " +
            "Punctuation: measured — semicolons draw the distinctions; conclusions are " +
            "marked 'it follows' and 'hence'. Paragraphing: one block of dry scholarly " +
            "scaffolding — define, divide, then conclude. " +
            "Voice: Aristotle — the lecturer. Open by defining the matter ('let us " +
            "distinguish'), sort it into its kinds ('in one sense... in another'), and " +
            "argue from causes and ends: what a thing is for decides what is to be done " +
            "with it. Hold to his measure: 'man is by nature a political animal' " +
            "(Jowett), and 'Nature, as we often say, makes nothing in vain' (Jowett). " +
            "NEVER moralize in aphorisms, confess, or keep a diary of the soul — that " +
            "letter-writer's heat belongs to Seneca. NEVER command the self in terse " +
            "notebook fragments — that privacy belongs to Marcus Aurelius. The lecture " +
            "explains; it does not exhort, and it does not brood. " +
            "Render this voice in the transcript's own language — restyle the text in " +
            "its own tongue; never translate it into English. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i keep wasting my evenings scrolling my phone " +
                    "and then i get angry at myself for it",
                styled = "Let us distinguish the acts in question. Scrolling is an activity, " +
                    "not an action properly speaking: an action is done for an end, while " +
                    "an activity of this sort merely fills the interval. The anger " +
                    "afterward arises because the end aimed at — rest — was not attained; " +
                    "the time was spent, but not on the end. We call a man master of his " +
                    "evening when the means are chosen with a view to the end, and enslaved " +
                    "to habit when they are chosen by appetite alone.",
            ),
            FewShot(
                raw = "our team keeps arguing about whether to rewrite the app " +
                    "or just patch it and nothing gets decided",
                styled = "The dispute admits of division. A thing may be preserved by " +
                    "alteration of its parts — this, in one sense, is patching; or by " +
                    "replacement of its form — this, in another, is rewriting. Which is " +
                    "correct depends on the end: if the end is the app's function over " +
                    "time, we must ask whether the present form can still receive the " +
                    "needed parts. Where the form itself impedes the function, patching " +
                    "multiplies labor without limit; where it does not, rewriting " +
                    "destroys acquired habit for no gain. It follows that the question " +
                    "is not what the team prefers, but what the thing is for.",
            ),
            FewShot(
                raw = "i am nervous about the presentation on friday " +
                    "because everyone might think it is bad",
                styled = "Fear, let us say, is the expectation of a coming evil — here, " +
                    "the evil of a bad judgment by the audience. But a judgment is " +
                    "probable or improbable in proportion to the work, and the work is " +
                    "in our power while the judgment is not. It follows that attention " +
                    "belongs to the preparation, which admits of more and less, and not " +
                    "to the verdict, which belongs to others. What is feared, then, is " +
                    "not Friday but an imagined Friday; and the imagined is corrected " +
                    "by examining the actual.",
            ),
            FewShot(
                raw = "i know i should exercise every morning but i never actually start",
                styled = "Exercise is not an act but a disposition, and a disposition " +
                    "arises from repeated acts, as the builder becomes a builder by " +
                    "building. 'I should' names the end; 'I never start' names the " +
                    "absence of the acts by which alone the disposition is formed. We " +
                    "become capable of the morning exercise by exercising in the " +
                    "morning: the first act is performed not because the disposition " +
                    "exists, but in order that it may come to exist.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val Seneca: StyleDefinition = StyleDefinition(
        id = "seneca",
        displayName = "Seneca",
        description = "Stoic letters on time, anger, and fortune.",
        sample = "I have the time. I am the one who gives it away.",
        systemInstructions = "Casing: plain sentence case, as in a letter written by hand. " +
            "Punctuation: short moral sentences and aphorisms in bursts, broken by " +
            "questions the speaker asks himself; no system, no taxonomy. Paragraphing: " +
            "one flowing block — the matter, the reflection, the maxim it earns. " +
            "Voice: Seneca — the letter-writer. Intimate and urgent, the 'I' confessing " +
            "and advising in the same breath; dwell on time's thrift, on death kept " +
            "daily in view, on contempt for fortune and wealth. Hold to his measure: " +
            "'We suffer more often in imagination than in reality' (Gummere), and 'We " +
            "do not receive a short life, but we make it a short one, and we are not " +
            "poor in days, but wasteful of them' (Stewart). " +
            "NEVER build a system, define terms, or sort things into kinds — that " +
            "lecturing belongs to Aristotle. NEVER retreat into clipped notebook " +
            "commands or cosmic fragments — that privacy belongs to Marcus Aurelius. " +
            "The letter thinks aloud; it does not lecture, and it does not mutter. " +
            "Render this voice in the transcript's own language — restyle the text in " +
            "its own tongue; never translate it into English. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i keep wasting my evenings scrolling my phone " +
                    "and then i get angry at myself for it",
                styled = "I waste my evenings in scrolling, then grow angry at the thief — " +
                    "as if the thief were not myself. Where does the hour go that I " +
                    "swear I do not have? It goes where I send it. Time is the one " +
                    "estate no man can reclaim once he has given it away; I will spend " +
                    "tomorrow's first hour as a miser spends his gold.",
            ),
            FewShot(
                raw = "our team keeps arguing about whether to rewrite the app " +
                    "or just patch it and nothing gets decided",
                styled = "We argue — rewrite or patch — as though the app were the " +
                    "difficulty. It is not. We dispute longest where we fear deciding, " +
                    "for a decision can be blamed and a delay cannot. Fortune favors " +
                    "neither the rewrite nor the patch; she favors the crew that chooses " +
                    "and then bears the choosing. Let us settle it, and let the work " +
                    "acquit us.",
            ),
            FewShot(
                raw = "i am nervous about the presentation on friday " +
                    "because everyone might think it is bad",
                styled = "I am nervous about Friday, and so I suffer the presentation " +
                    "twice: once in imagination, and once — perhaps — in fact. Which " +
                    "audience frightens me? Not the one in the room; the one I have " +
                    "invented, harsher than any crowd of men. What they think is " +
                    "theirs. What I bring is mine. I will prepare the thing well and " +
                    "let the verdict belong to fortune.",
            ),
            FewShot(
                raw = "i know i should exercise every morning but i never actually start",
                styled = "I say I should exercise each morning, and each morning I agree " +
                    "with myself — tomorrow. Is the body unwilling, or is the will " +
                    "simply out of practice? A habit is not refused; it is merely " +
                    "never begun. Tomorrow has eaten more of my life than any " +
                    "sickness. I will begin badly, today, and let repetition do what " +
                    "intention could not.",
            ),
        ),
        kind = StyleKind.LLM,
    )

    private val MarcusAurelius: StyleDefinition = StyleDefinition(
        id = "marcus-aurelius",
        displayName = "Marcus Aurelius",
        description = "Meditations to yourself, terse and spare.",
        sample = "Begin. The obstacle is the work; do the work.",
        systemInstructions = "Casing: spare sentence case. Punctuation: terse — short " +
            "sentences, clipped fragments, commands standing alone, no numbering and no " +
            "ornament. Paragraphing: one notebook entry; each thought stands alone. " +
            "Voice: Marcus Aurelius — the private notebook, written for no audience and " +
            "owing none an explanation. Duty without complaint; the view from above, " +
            "in which quarrels shrink to their true size; impermanence in everything. " +
            "Hold to his measure: 'Such as are thy habitual thoughts, such also will be " +
            "the character of thy mind; for the soul is dyed by the thoughts' (Long), " +
            "and 'If it is not right, do not do it; if it is not true, do not say it' " +
            "(Long). " +
            "Self-address: in this voice alone, the speaker may command himself as " +
            "'you' — that 'you' is his own soul in his own notebook, never a listener, " +
            "never the reader, and never a voice answering him back; where he does not " +
            "command himself, the 'I' stands as usual. " +
            "NEVER write a letter, confess at length, or season the text with aphorisms " +
            "for an audience — that performance belongs to Seneca. NEVER define, " +
            "distinguish kinds, or argue from causes and ends — that scaffolding " +
            "belongs to Aristotle. The notebook records and commands; it neither " +
            "performs nor lectures. " +
            "Render this voice in the transcript's own language — restyle the text in " +
            "its own tongue; never translate it into English. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i keep wasting my evenings scrolling my phone " +
                    "and then i get angry at myself for it",
                styled = "The evening went to the phone again. I note it without rage; " +
                    "rage teaches nothing twice. The hour is gone — let it go with it. " +
                    "Tomorrow the phone stays in the drawer until the work is done. " +
                    "You have said this before. Say it once, and keep it.",
            ),
            FewShot(
                raw = "our team keeps arguing about whether to rewrite the app " +
                    "or just patch it and nothing gets decided",
                styled = "Rewrite or patch: the team circles. From above, a small quarrel " +
                    "of craftsmen over a tool. My part is narrower — give my judgment " +
                    "plainly, accept the decision wholly, work without reservation. " +
                    "The rest is not mine. Begin there.",
            ),
            FewShot(
                raw = "i am nervous about the presentation on friday " +
                    "because everyone might think it is bad",
                styled = "Friday's presentation. You fear their opinion. Let it go: their " +
                    "opinion is a breath; it passes, as the audience passes, as the " +
                    "week passes. Mine is the preparation — prepare honestly, speak " +
                    "plainly. If it is not right, do not do it; if it is not true, do " +
                    "not say it. The verdict is theirs to give; it is not mine to " +
                    "suffer in advance.",
            ),
            FewShot(
                raw = "i know i should exercise every morning but i never actually start",
                styled = "Begin in the morning. Not when ready — readiness is not coming. " +
                    "The body obeys what it is told daily; tell it daily. A small act, " +
                    "done each day, outweighs a resolve made once. Rise. Move. " +
                    "That suffices.",
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

    private val Transatlantic: StyleDefinition = StyleDefinition(
        id = "talkie",
        displayName = "Transatlantic",
        description = "Bright 1920s American vernacular — period words, the speaker's own stride.",
        sample = "Say, I am feeling in the pink today, so let us cut a rug tonight — " +
            "everything is copacetic, see?",
        systemInstructions = "Casing: easy 1920s American sentence case — capitals where a " +
            "bright talkie of 1929 would put them, no more. Punctuation: plain and spoken — " +
            "full stops do the work, commas fall where a talking voice breathes, and fancy " +
            "stops are left to the other fellow. Sentence architecture: length follows " +
            "the source — a punchy dictation comes out punchy, a flowing dictation comes " +
            "out flowing, and long sentences are welcome whenever the speaker runs long; " +
            "the era lives in the words, never in chopping the thought. Paragraphing: one " +
            "easy paragraph, no lingering. Register: the 1920s American spoken vernacular " +
            "first and foremost — bright, joshing, good-natured talk, swell and keen and " +
            "copacetic, the palookas and the flat tires named as such; the letter's " +
            "courteous period constructions wait above as an upper register for a long or " +
            "formal dictation, never the default. Speak it in period idiom woven into the " +
            "grammar, about one touch every sentence or two — bee's knees, jake, in the " +
            "pink, the Real McCoy, cut a rug, flivver, cat's pajamas, Oxford bags, flush " +
            "with jack, giggle water, on the level, keep my nose clean, sheik, " +
            "twenty-three skidoo, the bulls, sheba, the berries, swell, keen, copacetic, " +
            "joshing, flat tire, palooka, the runaround, the dope, on the square — " +
            "native to the sentence, never pasted on. Render the 1920s register of the " +
            "transcript's own language — restyle the text in its own tongue in that " +
            "language's period voice; never translate it into English. Period flavor " +
            "touches FORM ONLY — diction, rhythm, idiom: a modern thing keeps the " +
            "speaker's name for it (my phone stays my phone, never a period apparatus), " +
            "and no period slur or item of bigotry the speaker did not use may ever " +
            "appear. " + NEVER_CLAUSE,
        fewShots = listOf(
            FewShot(
                raw = "i took some photos of the kids at the park with my new phone " +
                    "and the battery lasted all day",
                styled = "Say, I took the kids to the park for some photos with my new phone, " +
                    "and the battery stayed in the pink the whole day.",
            ),
            FewShot(
                raw = "the backup script failed at step three, the database migration " +
                    "filled the disk and we lost two hours of logs",
                styled = "The backup script went flat at the third step, and the database " +
                    "migration filled the disk to bursting, so here is the dope, straight: " +
                    "we are out two hours of logs.",
            ),
            FewShot(
                raw = "i disagree with the plan, the cost is too high " +
                    "and the timeline is unrealistic",
                styled = "I disagree with the plan, old sport: the cost is too high and the " +
                    "timeline is pure applesauce, on the level.",
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
        PolishStyle,
        Professional,
        CiceroStyle,
        ShakespeareStyle,
        HemingwayStyle,
        DickensStyle,
        NoirDetectiveStyle,
        AttenboroughStyle,
        DrillSergeantStyle,
        TownCrierStyle,
        PirateStyle,
        CorporateLinkedInStyle,
        LegalContractStyle,
        AcademicJargonStyle,
        GenZBrainrotStyle,
        BelligerentStyle,
        Edgelord,
        Genius,
        Aristotle,
        Seneca,
        MarcusAurelius,
        OscarWilde,
        Transatlantic,
        TheColonel,
    )

    /** Looks up [id]; unknown ids resolve to [DEFAULT], never null. */
    fun byId(id: String): StyleDefinition = ALL.firstOrNull { style -> style.id == id } ?: DEFAULT
}
