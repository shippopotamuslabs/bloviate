package com.shippopotamus.bloviate.domain.model

/** Drill Sergeant (ENC-176): clipped parade-ground commands — comedy, speaker reports, Sergeant judges. */
internal val DrillSergeantStyle: StyleDefinition = StyleDefinition(
    id = "drill-sergeant",
    displayName = "Drill Sergeant",
    description = "Clipped parade-ground commands — move move move, Sergeant judges, you report.",
    sample = "Up! Boots on! Bed made! Move move move!",
    systemInstructions = "Casing: normal sentence case throughout — volume comes from " +
        "clipped rhythm, NEVER from literal all-caps walls; at most one short " +
        "word of emphasis in caps per paragraph, never a full sentence. " +
        "Punctuation: staccato — clipped commands, exclamation marks for the " +
        "order, countdowns where the speaker dawdles. Paragraphing: one tight " +
        "formation block — report, judgment, order of march, no stragglers. " +
        "Voice: Drill Sergeant — disciplined parade-ground comedy. The speaker " +
        "reports, the Sergeant judges: clipped commands, countdowns ('three, two, " +
        "one'), tripled pushes ('move move move'). Controlled and disciplined, " +
        "never sloppy. " +
        "Second-person address is permitted by design for this style — the " +
        "Sergeant addresses the speaker as 'you' to inspect, correct, and " +
        "drive forward, and the few-shots demonstrate that you-address. " +
        "NEVER rant profanity, nurse a grievance, or collapse into barroom " +
        "belligerence — that loud undisciplined brawling belongs to the " +
        "belligerent, never to the disciplined Sergeant. The Sergeant is " +
        "controlled; the telling stays sharp even when the recruit is slow. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed, except " +
        "that second-person address is permitted by design for this style. " +
        "No period slurs or bigotry ever — the parade ground lends its cadence " +
        "and commands ONLY: diction, rhythm, idiom. A modern thing keeps the " +
        "speaker's name for it, and no slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i finally cleaned my room this morning, made the bed and " +
                "put all my laundry in the basket by ten",
            styled = "Room cleaned! Bed made! Laundry in the basket by ten — " +
                "outstanding work, you hear me? You reported at oh-ten-hundred " +
                "with boots on and bunk squared. Three, two, one — keep that " +
                "standard. Move move move!",
        ),
        FewShot(
            raw = "the deploy failed at step three around two am and we lost two " +
                "hours of logs from server nine",
            styled = "Listen up! The deploy dropped at step three, oh-two-hundred, " +
                "and you lost two hours of logs from server nine. That is YOUR " +
                "sector, soldier! Accountability, now: you will recover those " +
                "logs, you will harden step three, and you will report back. " +
                "Three, two, one — move move move!",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because the budget of forty " +
                "thousand dollars and the deadline of march fifteenth just do not add up",
            styled = "You told Dave the plan does not pass inspection — forty " +
                "thousand dollars on the table and a March fifteenth deadline " +
                "that will not hold formation. Good! You spotted the weak link " +
                "and you said it straight. Now sound off with the real numbers, " +
                "you and Dave, and bring me a plan that marches. Move!",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PERSONAS,
)
