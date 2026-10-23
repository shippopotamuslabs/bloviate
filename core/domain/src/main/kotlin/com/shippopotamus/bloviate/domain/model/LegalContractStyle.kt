package com.shippopotamus.bloviate.domain.model

/** Legal Contract (ENC-180): everything is contract prose — whereas-clauses, obligations, defined terms. */
internal val LegalContractStyle: StyleDefinition = StyleDefinition(
    id = "legal-contract",
    displayName = "Legal Contract",
    description = "Every errand an obligation — whereas-clauses, hereinafters, and defined terms.",
    sample = "WHEREAS the undersigned requires milk, NOW THEREFORE the Party shall procure " +
        "two gallons by Tuesday, time being of the essence.",
    systemInstructions = "Casing: formal legal sentence case — capitalise defined terms " +
        "and the first word of each clause; operative words (WHEREAS, NOW THEREFORE, " +
        "SHALL) in full capitals where the form demands. Punctuation: contractual — " +
        "semicolons join the recitals, commas carry the provisos, each obligation closed " +
        "with a full stop. Paragraphing: one flowing instrument — recitals, operative " +
        "clauses, and the closing obligation in a single block, no line breaks. " +
        "Voice: Legal Contract — every dictation is an instrument. Open with " +
        "WHEREAS recitals, name each errand an obligation with SHALL, bind persons " +
        "and things as defined terms in quotes (the \"Milk\", the \"Corner Store\"), " +
        "and fix parties with hereinafter formulae (\"hereinafter the Party\"). " +
        "Numbers and dates are obligations of record — emphasised, never rounded, " +
        "never dropped: sums, counts, days, and hours stand as written. Comedy by " +
        "register mismatch: the grander the boilerplate, the smaller the errand it " +
        "binds. Every obligation stays parseable — the reader can always tell what " +
        "the speaker must do, buy, or bring. " +
        "NEVER write a courtly letter of gracious sentiment or a plain business " +
        "memo — that courtesy belongs to The Colonel and the office, never to the " +
        "contract. The instrument binds; it does not charm, and it does not summarise. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No period slurs or bigotry ever — the form lends its recitals and " +
        "obligations ONLY: diction, rhythm, boilerplate. A modern thing keeps the " +
        "speaker's name for it, and no slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i need milk and eggs from the corner store on tuesday for three bucks",
            styled = "WHEREAS the Party (hereinafter the \"Purchaser\") requires milk " +
                "and eggs from the corner store; NOW THEREFORE the Purchaser shall " +
                "procure said milk and eggs on Tuesday for the recorded sum of three bucks, " +
                "time being of the essence.",
        ),
        FewShot(
            raw = "the backup failed at step three around two am and we lost two hours " +
                "of logs from server nine",
            styled = "WHEREAS the backup (hereinafter the \"Process\") failed at step " +
                "three around two am; NOW THEREFORE be it recorded that two hours of " +
                "logs from server nine stand lost, and the Process shall herein be " +
                "held to account for said two hours.",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because forty thousand dollars " +
                "by march fifteenth is too much",
            styled = "The undersigned told Dave that the plan (hereinafter the " +
                "\"Agreement\") is rejected; WHEREAS the Agreement obliges forty " +
                "thousand dollars by March fifteenth, NOW THEREFORE the sum is held " +
                "excessive and the obligation is declined of record.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.MODERN_PARODY,
)
