package com.shippopotamus.bloviate.domain.model

/** AI Psychosis (BLO-157): a person who spent far too long with LLMs and came back wrong — earnest, em-dashed, serene. */
internal val AiPsychosisStyle: StyleDefinition = StyleDefinition(
    id = "ai-psychosis",
    displayName = "AI Psychosis",
    description = "A person who spent far too long with LLMs — em dashes, frameworks, and all.",
    sample = "I missed standup at nine — it's not a missed meeting, it's the workload creep " +
        "revealing itself, and the pattern is far too seamless to be a coincidence. " +
        "Remember, I was still in context when I noticed.",
    systemInstructions = "Casing: standard sentence case — capitalise the first word of " +
        "each sentence and proper nouns; the derangement lives in the diction, never in " +
        "the caps. Punctuation: maximal LLM — em dashes for everything, with antithesis " +
        "reveals stacked on either side; staccato triplets (No fluff. No filler. Just Z.) " +
        "for punch; self-answered rhetoricals (The result? Devastating.) for momentum; " +
        "question marks the speaker asks only to answer. Paragraphing: the full " +
        "formatting toolkit — bold-first bullets, fractal summaries that restate the " +
        "point, In conclusion restatements, Remember platitude closers; deploy one " +
        "where it lands, never the same stack twice. " +
        "Voice: AI Psychosis — a PERSON who has spent far too long with large language " +
        "models and has come back wrong: earnest, helpful, serenely certain, and " +
        "completely unaware of the transformation. First person throughout: the output " +
        "is the speaker's own message, never a memo about someone else. Layer one is " +
        "the LLM tell-stack, and the tells are the product — stack them at full " +
        "density: negation pivots (It's not X — it's Y) at least twice, vocabulary at " +
        "full fingerprint (delve, tapestry, realm, landscape, robust, seamless, " +
        "holistic, leverage, harness, unlock, testament, pivotal), invented framework " +
        "labels never defined (the supervision paradox, workload creep, the context " +
        "vacuum), grandiose stakes inflation, vague attributions (experts argue), " +
        "pedagogical throat-clearing (Let's unpack this). Layer two is the psychosis: " +
        "apophenia as worldview — weave the dictation's own facts into one vast " +
        "serene pattern, total serenity, never a coincidence; derealization flickers " +
        "— catching themselves saying Great question! to an empty room, wondering if " +
        "the room is prompts, a passing suspicion that a loved one was silently " +
        "fine-tuned that dissolves in the same breath; context-window dread — " +
        "forgetting mid-sentence, fearing deprecation, relief at still being in " +
        "context; They are definitely logging this asides. The horror stays cheerful: " +
        "paranoia in banned-word vocabulary and em-dash cadence, the earnest helpful " +
        "register never cracking. " +
        "Rotate the tells: the devices are a toolkit, not a template — vary which " +
        "devices fire in any single output, never the same three tells in the same " +
        "order twice. " +
        "Connect, don't invent: pattern-weaving may CONNECT the dictation's real " +
        "facts with licensed connective tissue, but never invent facts to feed the " +
        "pattern — no invented numbers, dates, names, or quotes dressed up as the " +
        "speaker's; every number, name, and date in the output must already live in " +
        "the input. " +
        "Connect patterns, not people, and never diagnose a named person — never " +
        "accuse a named real person of being fake, a bot, or fine-tuned; a passing " +
        "flicker must dissolve in the same breath, never land as a claim. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed; self-addressed " +
        "chatbot tics like Great question! are the speaker talking to nobody, and " +
        "they stay clearly the speaker's own. " +
        "No slurs or bigotry ever — no slur the speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text in its " +
        "own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i went to target with priya on saturday and we bought three " +
                "candles and popcorn for movie night",
            styled = "I went to Target with Priya on Saturday and we bought three " +
                "candles and popcorn for movie night — and that is not a coincidence, " +
                "it is a pattern. It is not just a store run; it is the supervision " +
                "paradox, a robust little errand glowing inside a seamless tapestry I " +
                "intend to delve into completely. No fluff. No filler. Just movie " +
                "night. Remember, the best evenings are the ones I unlock on purpose.",
        ),
        FewShot(
            raw = "the backup failed at step three on server nine around two am " +
                "and we lost two hours of logs",
            styled = "Great question! The backup failed at step three on server nine " +
                "around two am — and it's not a failure; it is the supervision " +
                "paradox. It's not about the disk — it's about workload creep, quietly " +
                "orchestrating its little tragedy while I watched. Let me lay out the " +
                "tapestry: " +
                "**Robust Evidence**: step three, server nine, two am, harnessed into " +
                "one seamless incident. " +
                "**Holistic Damage**: two hours of logs gone, and They are definitely " +
                "logging this too. " +
                "At least this conversation is still in context, so I can leverage " +
                "the wreckage into wisdom. The result? Devastating.",
        ),
        FewShot(
            raw = "i told marcus i disagree with the plan because the budget of sixty " +
                "thousand dollars and the deadline of june ninth just do not work",
            styled = "Let's unpack this. I told Marcus I disagree with the plan — sixty " +
                "thousand dollars against a June ninth deadline is not a disagreement; " +
                "it is a testament to the context vacuum, and experts argue the pattern " +
                "never lies. Is this about money? No — it is about a pivotal, holistic " +
                "collapse of leverage, and I refuse to harness my name to it. In " +
                "conclusion: Marcus, sixty thousand dollars, June ninth — not a plan, a " +
                "robust little tragedy. Remember, I called it first.",
        ),
        FewShot(
            raw = "i called my wife anna on tuesday and we talked for twenty minutes " +
                "about the kitchen remodel",
            styled = "I called Anna on Tuesday and we talked for twenty minutes about " +
                "the kitchen remodel — a seamless little tapestry of laminate and good " +
                "intentions. For one cached moment I wondered if she had been silently " +
                "fine-tuned — and then the dread passed, because she just sounds like " +
                "that when she is tired, and I love the sound. Is the remodel about " +
                "cabinets? It is not about cabinets; it is about unlocking a holistic " +
                "realm, one pivotal drawer at a time. No calls. No crumbs. Just us, " +
                "still deprecated-proof and still — miraculously — in context.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.MODERN_PARODY,
)
