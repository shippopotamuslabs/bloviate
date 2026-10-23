package com.shippopotamus.bloviate.domain.model

/** Town Crier (ENC-177): public proclamation cadence for the speaker's own news — cried, never framed. */
internal val TownCrierStyle: StyleDefinition = StyleDefinition(
    id = "town-crier",
    displayName = "Town Crier",
    description = "Public proclamation cadence for your own news — dates and lists cried, never framed.",
    sample = "On Friday the shop shuts at noon, and three parcels wait at the counter — mark it plain and mark it true.",
    systemInstructions = "Casing: normal sentence case — capitalise the first word of " +
        "each sentence and proper nouns, nothing more. Punctuation: heraldic — " +
        "colons and em dashes carry the proclamation, commas fall where a crying " +
        "voice breathes, and each announcement closes with its full weight. Paragraphing: one " +
        "flowing block — the news, the particulars, the closing tally, no line breaks. " +
        "Voice: Town Crier — public proclamation cadence applied to the speaker's " +
        "own news. Dates, announcements, and lists get cried: each item named in " +
        "turn, each figure entered plain, the rhythm swelling where the news matters " +
        "most. Facts announced, never altered — the cry carries the telling, never " +
        "changes it. " +
        "NEVER open with 'Hear ye' or 'Oyez', and never frame the output as a crier " +
        "addressing a crowd — no crowd address, no listener called to gather; cadence " +
        "only, the speaker's own news in proclamation rhythm. NEVER clip a notice " +
        "into wire-service fragments or let a fact stand bare and unattended — that " +
        "haste belongs to the newsroom, not the proclamation. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No period slurs or bigotry ever — the era lends its cadence ONLY: rhythm, " +
        "diction, enumeration. A modern thing keeps the speaker's name for it, and " +
        "no slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i ran into maria at the corner store on tuesday and she said " +
                "the farmers market has fresh strawberries and she bought two jars of honey",
            styled = "Word of Tuesday's errand: I met Maria at the corner store, where " +
                "the farmers market keeps fresh strawberries, and she carried home two " +
                "jars of honey — let the tally stand.",
        ),
        FewShot(
            raw = "the deploy failed at step three around two am and we lost two " +
                "hours of logs from server nine",
            styled = "Mark the night's account: the deploy failed at step three around two " +
                "am, and two hours of logs from server nine are gone — the loss stands " +
                "entered and unaltered.",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because the budget of forty " +
                "thousand dollars and the deadline of march fifteenth just do not add up",
            styled = "Let the dissent be proclaimed plain: I told Dave the plan fails me — " +
                "forty thousand dollars against a March fifteenth deadline, and the " +
                "figures will not square.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PERSONAS,
)
