package com.shippopotamus.bloviate.domain.model

/** Gen Z Brainrot (ENC-182): contemporary internet vernacular full tilt, facts intact. */
internal val GenZBrainrotStyle: StyleDefinition = StyleDefinition(
    id = "gen-z-brainrot",
    displayName = "Gen Z Brainrot",
    description = "Chronically-online meme-lexicon chaos with the facts intact.",
    sample = "went to the store for milk no cap, aura intact, the lore stays unmatched",
    systemInstructions = "Casing: lowercase-energy — nearly everything lowercase, " +
        "proper nouns allowed to stay lowercase, capitals reserved for a rare " +
        "single-word emphasis hit, never full sentences of screaming caps. " +
        "Punctuation: loose and breathless — comma splices, fragments, and run-ons " +
        "welcome; keep ? and ! where the speaker's energy lands; no heavy formal " +
        "stops. Paragraphing: one flowing block — the bit, the lore drop, the " +
        "sign-off, no line breaks. " +
        "Register: contemporary internet vernacular full tilt — ironic, " +
        "hyper-referential, skibidi-adjacent chaos fluent in lore, aura, and no cap " +
        "asides; the slang rotates — never freeze a fixed wordlist — reach for the " +
        "current meme lexicon, but the intelligibility floor stays pinned, so " +
        "names, numbers, and objects always survive the bit. " +
        "NEVER chase transgression, shock, or dark nihilistic screaming caps — " +
        "that heat belongs to Edgelord, never to Gen Z Brainrot. Brainrot is " +
        "meme-lexicon absurdity and in-group fluency; it is not cruelty, and it " +
        "is not rage. " +
        "The output is the speaker's own words restyled — never a reply to the " +
        "user, never addressing a listener the input never addressed. " +
        "No slurs or bigotry ever — the chaos stays playful, and no slur the " +
        "speaker did not use may ever appear. " +
        "Render this voice in the transcript's own language — restyle the text " +
        "in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i went to target with priya on saturday and we bought three " +
                "candles and popcorn for movie night",
            styled = "went to target with priya on saturday no cap, secured three " +
                "candles and popcorn for movie night, the lore writes itself, aura intact",
        ),
        FewShot(
            raw = "the deploy failed at step four around one am and we lost ninety " +
                "minutes of logs from server twelve",
            styled = "deploy took an L at step four around one am fr, ninety minutes " +
                "of logs from server twelve just gone, down bad, no cap the incident " +
                "lore is insane",
        ),
        FewShot(
            raw = "i told marcus i disagree with the plan because the budget of sixty " +
                "thousand dollars and the deadline of june ninth just do not work",
            styled = "told marcus the plan is not it no cap, sixty thousand dollars on " +
                "the table plus a june ninth deadline is diabolical, aura mismatch fr, " +
                "the disagreement lore is real",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.MODERN_PARODY,
)
