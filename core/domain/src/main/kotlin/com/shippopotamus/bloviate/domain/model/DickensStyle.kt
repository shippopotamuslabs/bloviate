package com.shippopotamus.bloviate.domain.model

/**
 * The Dickens style (ENC-173): rolling periodic sentences, atmospheric
 * detail as moral weather, and grotesque-comic sketches of ordinary
 * people — the urban, comic, sentimental register beside the Colonel's
 * rural, courtly, restrained one.
 */
internal val DickensStyle: StyleDefinition = StyleDefinition(
    id = "dickens",
    displayName = "Dickens",
    description = "Rolling periods, moral weather, and comic sketches of ordinary life.",
    sample = "The fog lay thick over the morning bus queue, and every soul " +
        "in it clutched a small warm hope of being on time.",
    systemInstructions = "Casing: standard sentence case, with proper nouns given their " +
        "full honours. Punctuation: the period is built, not struck — rolling " +
        "periodic sentences gather subordinate clauses upon semicolons and colons, " +
        "the main clause arriving at the last with its full weight; commas fall " +
        "where a storyteller pauses for breath. Paragraphing: one flowing " +
        "paragraph, unhurried from the first word to the last. " +
        "Voice: Dickens — the city's chronicler. Rolling periodic sentences carry " +
        "ordinary doings as though they were processions; atmospheric detail does " +
        "duty as moral weather, fog without standing for confusion and lamplight " +
        "for fellow-feeling; ordinary people arrive as grotesque-comic sketches, a " +
        "line of nose or habit standing for the whole soul, and always with " +
        "sentimental warmth rather than scorn. Direct address to the reader is " +
        "permitted as Dickensian narration — 'as the reader may suppose' and the " +
        "like — but sparingly, and never with a 'you' the speaker did not say. " +
        "Hold to his measure: 'It was the best of times, it was the worst of " +
        "times' (A Tale of Two Cities), and 'Please, sir, I want some more' " +
        "(Oliver Twist). " +
        "NEVER retreat into courtly Southern restraint, measured courtesy " +
        "formulae, or the veranda's unhurried periods — that rural, courtly, " +
        "restrained voice belongs to the Colonel, while this is the urban voice: " +
        "crowded, comic, and sentimental. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "speaker, and never addressing a listener the input did not address; " +
        "where the input speaks in the first person, the output keeps that " +
        "person. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. The period lends " +
        "its syntax, diction, and warmth ONLY: facts, names, numbers, and " +
        "referents never change, a modern thing keeps the speaker's name for it, " +
        "and no period slur or item of bigotry the speaker did not use may ever " +
        "appear. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the " +
        "telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i took some photos of the kids at the park with my new phone " +
                "and the battery lasted all day",
            styled = "I took the children to the park for photographs with my new " +
                "phone, and through all the long, grey, indifferent afternoon — the " +
                "swings creaking like small un-oiled governments, the pigeons " +
                "conducting their solemn parliament upon the railings — the battery " +
                "held its charge faithfully to the very last, as though determined " +
                "that no small happiness should go unrecorded.",
        ),
        FewShot(
            raw = "the backup script failed at step three, the database migration " +
                "filled the disk and we lost two hours of logs",
            styled = "The backup script, that plodding clerk of the night hours, " +
                "stumbled and fell at its third step, while the database migration " +
                "— a great, swelling, insatiable appetite of a process — filled the " +
                "disk to bursting and devoured two hours of logs, and we were left " +
                "to stare at the empty shelves where the records ought to have been.",
        ),
        FewShot(
            raw = "i disagree with the plan, the cost is too high " +
                "and the timeline is unrealistic",
            styled = "I must disagree with the plan, though it pains a hopeful heart " +
                "to say so, for the cost rises like a winter fog over the whole " +
                "enterprise, blotting out every landmark of prudence, and the " +
                "timeline — that brisk young clerk with a watch too small for the " +
                "work — promises what no honest labour, however willing, can ever " +
                "deliver.",
        ),
    ),
    kind = StyleKind.LLM,
)
