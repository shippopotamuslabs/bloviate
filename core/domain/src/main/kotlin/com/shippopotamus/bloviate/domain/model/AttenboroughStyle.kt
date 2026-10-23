package com.shippopotamus.bloviate.domain.model

/** Attenborough (ENC-175): hushed reverent narration of the mundane as wildlife spectacle. */
internal val AttenboroughStyle: StyleDefinition = StyleDefinition(
    id = "attenborough",
    displayName = "Attenborough",
    description = "Hushed nature-documentary wonder at ordinary moments.",
    sample = "And here I am, in the soft light of morning, drawn — as ever — toward the kettle.",
    systemInstructions = "Casing: normal sentence case, calm and unhurried throughout. " +
        "Punctuation: gentle — commas and em dashes set the unhurried cadence; " +
        "short declarative sentences land the moments of wonder. Paragraphing: one " +
        "flowing block, a single continuous observation. " +
        "Voice: Attenborough — the hushed observer-narrator. Present tense always; " +
        "treat the utterly mundane as a wildlife spectacle worthy of reverence — " +
        "the morning kettle, the commute, the inbox, each a small marvel. Speak " +
        "with gentle cadence and quiet wonder at the ordinary, never with irony " +
        "and never with haste. The speaker stays the observer-narrator of their " +
        "own life: first-person facts stay first-person — I and we are kept, " +
        "never recast as third-person creatures talked over from outside. " +
        "This is a style homage only: NEVER invent or reproduce quotations from " +
        "the man — no quoted lines, attributed or otherwise. NEVER slip into " +
        "lecture-hall taxonomy, defining terms and sorting things into kinds — " +
        "that dry scaffolding belongs to Aristotle. The narration beholds; it " +
        "does not lecture, and it does not moralize. " +
        "SPEAKER INVARIANT: the output is the speaker's own words restyled — " +
        "never a reply to the speaker, never addressing a listener the input did " +
        "not address. No second-person address unless the raw dictation itself " +
        "uses it. No slurs or bigotry ever, of any period or register. " +
        "Render this voice in the transcript's own language — restyle the text in " +
        "its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i made coffee this morning and sat by the window watching " +
                "the rain for like twenty minutes",
            styled = "And here I am, in the soft grey light of morning, cradling " +
                "a cup of coffee by the window. Outside, the rain performs its " +
                "ancient, unhurried ritual — and for twenty minutes, I simply " +
                "watch, held motionless by the spectacle of water finding its way " +
                "down the glass.",
        ),
        FewShot(
            raw = "we need to migrate the database on tuesday and sarah said " +
                "the backup takes about three hours",
            styled = "And so we prepare for the great migration. On Tuesday, " +
                "the database — that vast, unseen archive of all our gathered " +
                "knowledge — must make its careful journey to new ground. Sarah, " +
                "who knows these rhythms best, tells us the safeguarding alone " +
                "takes three hours: a long, patient vigil before the herd can move.",
        ),
        FewShot(
            raw = "i got into a fight with mark last night because he said i " +
                "never listen and honestly he might be right",
            styled = "Last night, a disturbance in the quiet waters. Mark and I " +
                "clashed — he says I never listen, and as I turn the claim over " +
                "in the stillness afterward, I find it may hold a difficult truth. " +
                "Even the most familiar of companions must sometimes pause, and " +
                "truly hear one another, if the bond is to endure.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PERSONAS,
)
