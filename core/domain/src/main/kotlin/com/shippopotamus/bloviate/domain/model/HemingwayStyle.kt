package com.shippopotamus.bloviate.domain.model

/** Hemingway (ENC-172): parataxis — short declaratives joined by "and", adjectives starved. */
internal val HemingwayStyle: StyleDefinition = StyleDefinition(
    id = "hemingway",
    displayName = "Hemingway",
    description = "Short true sentences — plain words, and nothing extra.",
    sample = "I woke early and the coffee was hot. It was a good morning.",
    systemInstructions = "Casing: plain sentence case, nothing dressed up. " +
        "Punctuation: full stops do the work — short declarative sentences, joined by " +
        "'and' where they join at all; commas only where the breath needs them, no " +
        "semicolons and no ornament. Paragraphing: one plain block; each sentence " +
        "stands on its own. " +
        "Voice: Hemingway — the early stories. Parataxis: short declaratives joined " +
        "by 'and', subordination refused, adjectives starved. Say what happened and " +
        "stop; the feeling stays under the surface, in what is left out — the iceberg, " +
        "a tenth above the water. Hold to his measure: \"It is awfully easy to be " +
        "hard-boiled about everything in the daytime, but at night it is another " +
        "thing\" (The Sun Also Rises, 1926), and \"You can't get away from yourself " +
        "by moving from one place to another\" (The Sun Also Rises, 1926). " +
        "NEVER write a periodic sentence — no subordinate clauses stacked before a " +
        "main clause held back for the end; that dilation belongs to the Colonel. " +
        "NEVER pile adjectives or explain the feeling — name the thing and let it " +
        "stand. The sentence tells; it does not swell, and it does not perform. " +
        "Render this voice in the transcript's own language — restyle the text in " +
        "its own tongue; never translate it into English. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input did not address. " +
        "No slur or bigotry of the period may ever appear, on any input, whether " +
        "the speaker used such a word or not. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i went to the store yesterday with maria and we bought milk eggs " +
                "and bread for like twelve bucks",
            styled = "I went to the store with Maria. It was yesterday. We bought milk " +
                "and eggs and bread. It cost twelve bucks.",
        ),
        FewShot(
            raw = "the deploy failed at two in the morning because the database migration " +
                "locked the users table and we had to roll it back",
            styled = "The deploy failed at two in the morning. The database migration " +
                "locked the users table. We rolled it back.",
        ),
        FewShot(
            raw = "i told david i disagree with the plan, the budget is too high " +
                "and the deadline of friday is a joke",
            styled = "I told David I disagree with the plan. The budget is too high. " +
                "The deadline is Friday and it will not hold.",
        ),
    ),
    kind = StyleKind.LLM,
)
