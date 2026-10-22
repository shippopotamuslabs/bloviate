package com.shippopotamus.bloviate.domain.model

/** Academic Jargon (ENC-181): impenetrable-but-grammatical postmodern seminar prose. */
internal val AcademicJargonStyle: StyleDefinition = StyleDefinition(
    id = "academic-jargon",
    displayName = "Academic Jargon",
    description = "Dense postmodern seminar prose — problematises, interrogates, and never quite says the thing.",
    sample = "The Tuesday coffee run with Priya thus emerges as a privileged site of caffeinated sociality.",
    systemInstructions = "Casing: formal sentence case — capitalise the first word of " +
        "each sentence and proper nouns, nothing more; diction does the fogging, " +
        "never the capitals. Punctuation: impeccable — long periodic sentences, " +
        "semicolons and em dashes staging the subordinate clauses, commas exactly " +
        "where grammar demands them. Paragraphing: one dense seminar block — the " +
        "problematisation, the interrogation, the situated closing, no line breaks. " +
        "Voice: Academic Jargon — impenetrable-but-grammatical postmodern seminar " +
        "prose that problematises the mundane, interrogates the obvious, and relocates " +
        "every anecdote onto sites of contested meaning within overlapping " +
        "discourses — of domesticity, of labor, of affect. Grammar stays impeccable " +
        "throughout; only the diction fogs. Fidelity warning: this register pushes " +
        "the never-change guard hardest of any voice, so every name, number, date, " +
        "and quoted fragment must survive the fog intact — the telling thickens, " +
        "the facts never move. " +
        "NEVER collapse into plain clear technical prose or a crisp professional " +
        "summary — that lucidity belongs to the Genius and the Professional, never " +
        "to the seminar, which thickens where they would clarify. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No period slurs or bigotry ever — the seminar lends its diction and " +
        "syntax ONLY: problematising, interrogating, situating. A modern thing " +
        "keeps the speaker's name for it, and no slur the speaker did not use " +
        "may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i grabbed coffee with priya on tuesday and we walked rex " +
                "around the park for twenty minutes",
            styled = "The Tuesday coffee encounter with Priya, extended by a " +
                "twenty-minute perambulation around the park with Rex, problematises " +
                "the boundary between caffeinated sociality and quotidian care labor, " +
                "interrogating the park itself as a site of interspecies leisure " +
                "within overlapping discourses of friendship and routine.",
        ),
        FewShot(
            raw = "the backup script failed at step three, the database migration " +
                "filled the disk and we lost two hours of logs from server nine",
            styled = "The backup script's failure at step three, wherein the database " +
                "migration filled the disk and occasioned the loss of two hours of " +
                "logs from server nine, constitutes a privileged site of infrastructural " +
                "precarity — a moment that interrogates the fantasy of seamless " +
                "automation and problematises backup as a discourse of promised " +
                "continuity perpetually deferred.",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because the budget of forty " +
                "thousand dollars and the deadline of march fifteenth just do not add up",
            styled = "The communicated disagreement with Dave regarding the plan — " +
                "a budget of forty thousand dollars set against a deadline of March " +
                "fifteenth — interrogates the plan as a site of fiscal-temporal " +
                "contradiction, problematising its arithmetic within competing " +
                "discourses of ambition and feasibility.",
        ),
    ),
    kind = StyleKind.LLM,
)
