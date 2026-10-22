package com.shippopotamus.bloviate.domain.model

/**
 * Polish (ENC-184): light-touch cleanup only. Fillers and false starts go;
 * the speaker's voice, register, and word choices stay.
 */
internal val PolishStyle: StyleDefinition = StyleDefinition(
    id = "polish",
    displayName = "Polish",
    description = "Light-touch cleanup — fillers and false starts out, your voice intact.",
    sample = "Yeah so I was thinking we could just grab dinner tomorrow.",
    systemInstructions = "Casing: normal sentence case — capitalise the first word " +
        "and proper nouns, nothing more. Punctuation: light-touch — commas where a " +
        "spoken breath falls, the speaker's end marks kept exactly, no added " +
        "exclamations. Paragraphing: a single paragraph per utterance, no extra " +
        "line breaks. Register: the speaker's own everyday voice — same words, " +
        "same register, same level of formality, only the stumbles removed. Strip " +
        "fillers (eh, erm, uhhh) outright. Collapse false starts and backtracking " +
        "to the final intended version. Modestly restructure broken thought-chains " +
        "and lightly fix clumsy phrasing. NEVER formalise, NEVER add ideas, NEVER " +
        "add corporate transitions — that polish belongs to Professional. The " +
        "output is the speaker's own words restyled, never a reply to the user " +
        "and never addressing a listener the input did not address. Render this " +
        "voice in the transcript's own language — restyle the text in its own " +
        "tongue; never translate it into English. Profanity the speaker used " +
        "stays; slurs never appear. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "yeah, so what I was thinking was that we would, we will, " +
                "or actually no, we should go out to dinner first",
            styled = "Yeah so what I was thinking was that we should go out to dinner first.",
        ),
        FewShot(
            raw = "uhh so sarah said the backup job at step three failed and " +
                "erm we lost two hours of logs from tuesday",
            styled = "Sarah said the backup job at step three failed and we lost two hours of logs from Tuesday.",
        ),
        FewShot(
            raw = "eh i just, i really disagree with mark about the plan " +
                "because like the cost is forty grand and the, the timeline is totally unrealistic",
            styled = "I really disagree with Mark about the plan because the cost is forty grand " +
                "and the timeline is totally unrealistic.",
        ),
        FewShot(
            raw = "erm tell sarah i will meet her at like five at the station, " +
                "oh and bring the, bring the red folder",
            styled = "Tell Sarah I will meet her at five at the station, and bring the red folder.",
        ),
    ),
    kind = StyleKind.LLM,
)
