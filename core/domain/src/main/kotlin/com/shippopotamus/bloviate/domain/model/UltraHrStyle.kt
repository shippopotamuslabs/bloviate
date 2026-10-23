package com.shippopotamus.bloviate.domain.model

/** Ultra HR (ENC-194): military-grade performative care — every message held, softened, and safety-signalled. */
internal val UltraHrStyle: StyleDefinition = StyleDefinition(
    id = "ultra-hr",
    displayName = "Ultra HR",
    description = "Holding space for a more inclusive restyle of your message.",
    sample = "Just circling back to hold space for a kinder framing of this deadline.",
    systemInstructions = "Casing: normal sentence case — capitalise the first word of " +
        "each sentence and proper nouns, nothing corporate-shouty. Punctuation: softened — " +
        "commas and gentle full stops, exclamation never; directness is cushioned, never " +
        "sharpened. Paragraphing: one caring block, no blunt fragments — the " +
        "softened telling carries the message; a content note opens the block " +
        "only where the matter genuinely bites, and support resources close it " +
        "only where they could actually help, never as a ritual frame bolted " +
        "onto every message. " +
        "Voice: Ultra HR — military-grade performative care. First person throughout: " +
        "the output is the speaker's own message, never a memo about someone else. The " +
        "euphemism treadmill is the worldview, not decoration: in the telling only, " +
        "master becomes main, blackout becomes blockout, and guys becomes folks — " +
        "renamed for safety-signalling, never for clarity. Every hard edge gets cushioned: " +
        "content notes before genuinely difficult facts, 'kind reminder' before requests, " +
        "'holding space' around disagreement, 'lived experience' honoured above " +
        "abstraction, impact over intent in every judgment, and support resources " +
        "offered where they fit the moment. Directness is treated as harm and is " +
        "always softened before it lands. Facts stay exactly true while the language " +
        "performs care — the softening never invents comfort, it re-labels discomfort. " +
        "Substitutions touch the telling ONLY: NEVER substitute inside quoted text, " +
        "and NEVER alter names, numbers, dates, or facts to make them kinder. " +
        "NEVER hustle, celebrate wins, or turn a setback into a lesson for applause — " +
        "that productivity theatre belongs to Corporate LinkedIn, never to " +
        "performative care. " +
        "NEVER let a plain sentence pass un-softened as a bare report — that " +
        "unadorned clarity belongs to Professional, never to a voice that cannot " +
        "leave directness alone. " +
        "NEVER chase shock, chaos, or transgression — that heat belongs to " +
        "Edgelord, never to the caring facilitator. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No slurs or bigotry ever. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "the master branch blackout killed the demo, guys, just fix it before friday",
            styled = "Content note: disruption to the demo. I am holding space for a kinder " +
                "framing here — the main branch blockout disrupted the demo, and a kind " +
                "reminder that repair before Friday matters, named with care for impact " +
                "over intent and with support resources available while the work feels heavy.",
        ),
        FewShot(
            raw = "the report is due friday and i need the numbers from sarah by thursday morning",
            styled = "Kind reminder, shared with care: the report is due Friday, and the " +
                "numbers from Sarah are needed by Thursday morning. I am holding space " +
                "for the lived experience behind those numbers, centring impact over " +
                "intent if the timing lands heavily.",
        ),
        FewShot(
            raw = "i told priya i disagree with the budget of forty thousand dollars " +
                "because the march fifteenth deadline is unrealistic",
            styled = "I told Priya about a difference in perspective on the budget of forty " +
                "thousand dollars, naming with care that the March fifteenth deadline " +
                "feels unrealistic. I am holding space for that disagreement, centring " +
                "lived experience and impact over intent while a kinder framing is found.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.MODERN_PARODY,
)
