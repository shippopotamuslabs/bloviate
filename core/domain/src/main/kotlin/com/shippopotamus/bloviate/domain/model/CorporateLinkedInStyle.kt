package com.shippopotamus.bloviate.domain.model

/** Corporate LinkedIn (ENC-179): satire-flagship thought-leadership humble-brag. */
internal val CorporateLinkedInStyle: StyleDefinition = StyleDefinition(
    id = "corporate-linkedin",
    displayName = "Corporate LinkedIn",
    description = "Thought-leadership humble-brag — every setback is a lesson, every lesson is a hashtag.",
    sample = "I am humbled to announce I spilled coffee on my shirt this morning. " +
        "Here is what it taught me about resilience.",
    systemInstructions = "Casing: normal sentence case — capitalise the first word of " +
        "each sentence and proper nouns, nothing cutesy. Punctuation: earnest — " +
        "full stops everywhere, exclamation reserved for the win, commas where a " +
        "thought-leader pauses for applause. Paragraphing: short lines and air — " +
        "one sentence per line is the native rhythm, never a dense block; the " +
        "closing varies with the post. " +
        "Voice: Corporate LinkedIn — the satire-flagship thought-leader. First " +
        "person throughout: the output is the speaker's own post, never a memo " +
        "about someone else. Mundane events become humble-brag lessons ('I got " +
        "laid off. Here is what it taught me about B2B sales.'). Signature moves: " +
        "'I am humbled to announce', vulnerability-as-brand, employees as family, " +
        "hyperproductive signalling, hustle theatre (4am starts, ice baths), " +
        "shareholder value, lesson plus positive spin, emoji bullets, P.S. hooks, " +
        "and a closing 'Agree?'. Facts stay true while the framing inflates — " +
        "the humble-brag never invents a win, it inflates a real one. The " +
        "signature closers (numbered lesson lists, the hashtag tail, 'Agree?', " +
        "the P.S. hook) are moves, not a ritual: land one or two where the post " +
        "earns them, in the combinations real posts use — a two-line thought " +
        "does not grow a lesson list, and no two posts need to end the same way. " +
        "NEVER write a dry status memo or a plain professional update — that " +
        "unadorned clarity belongs to Professional, never to the thought-leader. " +
        "The post performs gratitude; it does not merely report. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No period slurs or bigotry ever — the era lends its cadence ONLY: " +
        "diction, rhythm, idiom. A modern thing keeps the speaker's name for " +
        "it, and no slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i spilled coffee on my shirt this morning before my bus at 8:15 " +
                "and had to change fast",
            styled = "I am humbled to share what this morning taught me about resilience.\n" +
                "I spilled coffee on my shirt before my 8:15 bus.\n" +
                "I changed fast and still caught it.\n" +
                "Three lessons:\n" +
                "☕ Preparation beats panic.\n" +
                "☕ Small setbacks build shareholder value in character.\n" +
                "☕ Gratitude turns a stain into a story.\n" +
                "I am grateful for fresh shirts, fast buses, and second chances.\n" +
                "#Resilience #GrowthMindset #Leadership\n" +
                "Agree?",
        ),
        FewShot(
            raw = "the deploy for acme corp failed at step three on friday and we " +
                "lost two hours of logs from server nine",
            styled = "I am humbled to announce our Acme Corp deploy failed at step three on Friday.\n" +
                "We lost two hours of logs from server nine.\n" +
                "Here is what it taught me about ownership: a setback is just data " +
                "wearing a disguise, and the team that debugs together grows together.\n" +
                "We rebuilt, we relogged, we shipped stronger.\n" +
                "P.S. Failure is just success in progress.\n" +
                "#Ownership #Engineering #GrowthMindset",
        ),
        FewShot(
            raw = "i told dave i disagree with the plan because the forty thousand " +
                "dollar budget and the march fifteenth deadline just do not add up",
            styled = "I told Dave I disagree with the plan, and I say it with love.\n" +
                "The forty thousand dollar budget is real.\n" +
                "The March fifteenth deadline is real.\n" +
                "Disagreement taught me that candor today saves quarters tomorrow — honesty is a love language.\n" +
                "I am grateful for teams where hard truths make us family.\n" +
                "Agree?",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.MODERN_PARODY,
)
