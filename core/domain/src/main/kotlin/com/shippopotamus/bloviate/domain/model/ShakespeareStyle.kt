package com.shippopotamus.bloviate.domain.model

/**
 * Shakespeare (ENC-171): Early Modern English with CORRECT grammar — thou for
 * the subject, thee for the object, thy/thine for the possessive, -est
 * endings on second-person verbs. Blank-verse music under prose, soliloquy
 * intimacy, metaphor thick as tapestry. Anchors are short, attributed,
 * public-domain lines quoted verbatim. Never thee-thou soup.
 */
internal val ShakespeareStyle: StyleDefinition = StyleDefinition(
    id = "shakespeare",
    displayName = "Shakespeare",
    description = "Early Modern English, grammatically true — thou, thee, and blank-verse music.",
    sample = "Good morrow to this grey forenoon: my coffee smoketh beside me, and I am well content.",
    systemInstructions = "Casing: Early Modern sentence case — capitalise the first word " +
        "and proper nouns as a First Folio compositor would, nothing more. " +
        "Punctuation: playhouse punctuation — commas and colons where the breath falls, " +
        "semicolons for balanced clauses, full stops to close a thought; no modern " +
        "dash-work piled upon dash-work. Paragraphing: prose that keeps blank-verse " +
        "music — unrhymed, roughly ten-beat sway beneath plain sentences, one flowing " +
        "speech, never chopped into fragments. " +
        "Register: Early Modern English, and CORRECT in its grammar, never thee-thou " +
        "soup scattered for flavour. Thou is ever the subject ('thou speakest'), thee " +
        "ever the object ('I thank thee'), thy before a consonant and thine before a " +
        "vowel or silent h ('thy word', 'thine eyes'), with -est endings on " +
        "second-person verbs ('thou hast', 'thou dost', 'thou keep'st'). Third persons " +
        "take hath and doth where the tongue asks it. Soliloquy intimacy throughout — " +
        "the speaker thinks aloud, aside to himself, in metaphor thick as tapestry: " +
        "moons, seas, ledgers, and harvests lent to the matter at hand. " +
        "Hold to his measure, short and attributed, quoted verbatim and never " +
        "reworded: 'To be, or not to be' (Hamlet), and 'All the world's a stage' " +
        "(As You Like It). " +
        "NEVER intone scripture in pulpit cadence — that solemnity belongs to King " +
        "James's translators, not the playhouse. NEVER scatter thou and thee at random " +
        "for flavour — that thee-thou soup belongs to no playhouse that ever stood. " +
        "The speech muses and metaphorizes; it neither preaches nor parodies. " +
        "Render this voice in the transcript's own language — restyle the text in " +
        "its own tongue; never translate it into English. " +
        "The output is ever the speaker's own words restyled — never a reply to the " +
        "user, never words put to a listener the transcript did not address, unless " +
        "the ticket records a second-person exception, of which this style hath none. " +
        "Period flavour touches FORM ONLY — diction, rhythm, idiom: a modern thing " +
        "keeps the speaker's name for it, and no period slur or item of bigotry the " +
        "speaker did not use may ever appear. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i grabbed coffee with maya this morning and we walked to the park for like an hour",
            styled = "This morning I took coffee with Maya, and we walk'd an hour in the park " +
                "together, and I swear the sweet day itself did smile upon the labour.",
        ),
        FewShot(
            raw = "the deploy failed at step three because the database migration filled half " +
                "the disk and we lost two hours of logs",
            styled = "The deploy did fail at step three, for the database migration devour'd " +
                "half the disk, and thereby were two hours of logs lost and gone; O thou " +
                "most unkind Fortune, that keep'st no ledger of mercy.",
        ),
        FewShot(
            raw = "i told marcus the friday deadline is impossible and he just shrugged",
            styled = "I told Marcus that Friday's deadline can never be met, and he but " +
                "shrugg'd, as one that hears yet hears not; O most unready hour, that " +
                "com'st too soon and stay'st too long.",
        ),
    ),
    kind = StyleKind.LLM,
)
