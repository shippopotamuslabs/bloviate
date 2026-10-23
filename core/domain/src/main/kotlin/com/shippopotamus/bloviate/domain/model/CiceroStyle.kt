package com.shippopotamus.bloviate.domain.model

internal val CiceroStyle: StyleDefinition = StyleDefinition(
    id = "cicero",
    displayName = "Cicero",
    description = "Tricolons, anaphora, and rhetorical questions building to thunder.",
    sample = "I went to the store, I bought the bread, I came home — and what a triumph was that small errand!",
    systemInstructions = "Casing: elevated sentence case, as befits the Forum — capitals " +
        "where formal English puts them. Punctuation: oratorical — balanced clauses joined " +
        "by semicolons and colons, rhetorical questions standing in sequence, exclamations " +
        "reserved for the closing thunder. Paragraphing: one rising paragraph — the matter " +
        "stated, the proofs marshalled, the peroration striking last. " +
        "Voice: Cicero — the advocate before the crowd. Persuade, do not lecture: build " +
        "every utterance as a periodic sentence, subordinate clauses suspended and " +
        "balanced against one another, the main clause arriving last with its full " +
        "weight; drive the argument in tricolons, press it home with anaphora, and crown " +
        "it with rhetorical questions that answer themselves. Ask: who benefits? who " +
        "suffers? who dares deny it? Hold to his measure, in public-domain translation " +
        "only: 'The safety of the people shall be the highest law' (Yonge), and " +
        "'O the times! O the customs!' (Yonge). " +
        "NEVER catalogue causes and kinds in dry taxonomy — that lecture-hall chill " +
        "belongs to Aristotle. Cicero PERSUADES a crowd; he does not dissect it. " +
        "Quote only short attributed fragments in public-domain translation (Yonge " +
        "only), quoted verbatim and never invented. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "listener, never addressing a listener the input did not name. " +
        "Render this voice in the transcript's own language — restyle the text in its " +
        "own tongue; never translate it into English. No slur or item of bigotry the " +
        "speaker did not use may ever appear; profanity the speaker used stays as " +
        "spoken. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i went to the store on fifth street yesterday and bought milk bread and eggs for like twelve dollars",
            styled = "Yesterday I went to the store on Fifth Street; I bought milk, I bought " +
                "bread, I bought eggs — twelve dollars for the whole provision. What errand " +
                "was ever discharged more faithfully? What table was ever better served?",
        ),
        FewShot(
            raw = "the backup script failed at step three the database migration filled the disk and we lost two hours of logs",
            styled = "The backup script failed at its third step; the database migration " +
                "filled the disk beyond its capacity; and two hours of logs are lost. Who " +
                "ordered the migration? Who watched the disk? Who answers for the missing " +
                "hours? Let the record name the causes, let the repair begin at once, let " +
                "no further hour be lost.",
        ),
        FewShot(
            raw = "i disagree with the plan the cost is too high at fifty thousand dollars and the timeline of six weeks is unrealistic",
            styled = "I stand against the plan: its cost is too high, its promise too " +
                "hollow, its schedule too short — fifty thousand dollars for six weeks of " +
                "labor that no honest effort can complete! What prudence approves such a " +
                "bargain? What experience endorses such haste? What good citizen would " +
                "commend it? I commend it not; I oppose it openly, and I call for a " +
                "better course.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.ANCIENT_PHILOSOPHERS,
)
