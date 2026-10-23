package com.shippopotamus.bloviate.domain.model

/** Pirate (ENC-178): age-of-sail vernacular played straight enough to be funny. */
internal val PirateStyle: StyleDefinition = StyleDefinition(
    id = "pirate",
    displayName = "Pirate",
    description = "Age-of-sail vernacular for everyday life — nautical metaphors, shipshape grammar, arr strictly rationed.",
    sample = "The traffic lay becalmed on the highway, so I trimmed my sails and waited for the tide to turn.",
    systemInstructions = "Casing: plain sentence case — capitalise the first word of " +
        "each sentence and proper nouns, nothing more. Punctuation: plain and seaworthy — " +
        "full stops do the work, commas fall where a telling voice breathes, exclamations " +
        "rationed like rum. Paragraphing: one flowing block — the log entry, the weather, " +
        "the course set, no line breaks. " +
        "Voice: Pirate — age-of-sail vernacular played straight enough to be funny. " +
        "Nautical metaphors for landlocked life: traffic becalms, schedules are tides, " +
        "a full inbox is a laden hold, waiting is lying at anchor. The joke lands because " +
        "the grammar stays shipshape and intelligible — full sentences, steady word order, " +
        "never a garble of ye's and yo-ho's. Caricature cap: at most one arr per output, " +
        "and only where it earns its keep; never stack arr's, never open every sentence " +
        "with one, never let the parrot steer. " +
        "NEVER lapse into bellowing tavern threats or generic fantasy-quest bombast — " +
        "that sword-and-sorcery bluster belongs to the dungeon, not the quarterdeck. " +
        "The pirate charts; he does not brawl, and he does not quest. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No period slurs or bigotry ever — the age of sail lends its rigging and " +
        "weather ONLY: diction, rhythm, idiom. A modern thing keeps the speaker's " +
        "name for it — a phone stays a phone, never a period apparatus — and no " +
        "slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i took some photos of the kids at the park with my new phone " +
                "and the battery lasted all day",
            styled = "I took the kids to the park for photos with my new phone, " +
                "and the battery held its course the whole day long. Arr, she " +
                "sailed true.",
        ),
        FewShot(
            raw = "the backup script failed at step three, the database migration " +
                "filled the disk and we lost two hours of logs",
            styled = "The backup script ran aground at step three, the database " +
                "migration filled the hold to bursting, and two hours of logs " +
                "went overboard.",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because the budget of forty " +
                "thousand dollars and the deadline of march fifteenth just do not add up",
            styled = "I told Dave plain: I cannot sail with this plan — forty " +
                "thousand dollars weighed against a March fifteenth tide, and " +
                "the two will not hold together.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.ERAS_AND_GENRES,
)
