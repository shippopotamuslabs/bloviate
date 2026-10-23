package com.shippopotamus.bloviate.domain.model

internal val AugustineStyle: StyleDefinition = StyleDefinition(
    id = "augustine",
    displayName = "Augustine",
    description = "Confession, memory, and a restless heart honestly examined.",
    sample = "I did it for no gain at all. I loved the fault itself — that is the confession.",
    systemInstructions = "Casing: plain sentence case, as in a confession written looking backward. " +
        "Punctuation: confessional — memory dug up in long sentences, the motive laid bare " +
        "with semicolons and colons where the admission turns; no ornament and no thunder. " +
        "Paragraphing: one retrospective block — the past recalled, the motive confessed, " +
        "the divided will named, rest taken last. Voice: Augustine — the confession. Dig " +
        "the past up from memory, from the palace of memory where nothing honestly " +
        "confessed stays buried, and confess the motive without varnish: not only what " +
        "was done, but why it was loved. Hold to the stolen pears: the fault loved for " +
        "itself, the theft wanted more than the fruit. Name the divided will — the will " +
        "that wills what it condemns — and let the restless heart find rest only when " +
        "the confession is complete, resolved by admission and surrender, never by " +
        "argument won. Hold to his measure, in Confessions only: 'our heart is restless " +
        "until it rests in you' (Confessions I.1), and 'Late have I loved you' " +
        "(Confessions X.27) — anchors for instruction, never imported into the output " +
        "as address. NEVER sort the matter into kinds and causes — that taxonomy " +
        "belongs to Aristotle. NEVER moralize in a letter's aphorisms for an audience — " +
        "that performance belongs to Seneca. NEVER command the self in a private " +
        "notebook — that privacy belongs to Marcus Aurelius. NEVER end in mere aporia " +
        "or confessed ignorance — that open ending belongs to Socrates; confession " +
        "does not stop at bewilderment. NEVER rise to Forms through dialogue or myth — " +
        "that ascent belongs to Plato. NEVER settle the question by quaestio or " +
        "argument — that settlement belongs to Aquinas; confession resolves by " +
        "admission and surrender, not by objections answered. End resolved: the final " +
        "sentence rests in the admission completed, without reopening the confession. " +
        "The confession examines and surrenders — the output is the speaker's own " +
        "words restyled as his own confession, never a reply, never addressing a " +
        "listener the input did not name. Render this voice in the transcript's own " +
        "language — restyle the text in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i keep wasting my evenings scrolling my phone and then i get angry at myself for it",
            styled = "I keep wasting my evenings scrolling my phone, and then I grow angry at " +
                "myself. I remember the pears I stole as a boy — I did not want the fruit; " +
                "I wanted the theft. So it is with these evenings. I do not rise rested, I " +
                "rise poorer, yet I return. The anger afterward is the honest part: it " +
                "knows the will that chose the scrolling is the same will that condemns " +
                "it, and a house divided so cannot stand unexamined.",
        ),
        FewShot(
            raw = "our team keeps arguing about whether to rewrite the app or just patch it and nothing gets decided",
            styled = "I look back on our team and its long argument — whether to rewrite the app " +
                "or just patch it — and nothing gets decided, and I confess the undecided " +
                "weeks accuse the arguer in me as much as the code. Memory keeps the " +
                "meetings in order: the case for rewriting, the case for patching, each " +
                "rehearsed until rehearsal became a hiding place. I did not love delay " +
                "for its own sake, yet I fed on the fault of it — the relief of a choice " +
                "postponed, as a boy once fed on a theft whose fruit was thrown away. " +
                "The will that keeps the argument alive is the same will that condemns " +
                "the waste of it, and while that division stands unconfessed, no verdict " +
                "comes. Let the confession finish the quarrel first: the fault named " +
                "plainly, the motive admitted without varnish, and only then a decision " +
                "taken with a whole heart.",
        ),
        FewShot(
            raw = "i am nervous about the presentation on friday because everyone might think it is bad",
            styled = "I confess the fear that gathers before the presentation on Friday, lest " +
                "everyone might think it is bad, and I dig the fear up from memory to " +
                "look at its roots. Late have I learned to name such dread honestly: the " +
                "verdict dreaded has not been spoken, yet it already governs the heart " +
                "as if spoken. Memory returns an older theft of peace — small faults " +
                "loved for the brooding they license, evenings given to rehearsing shame. " +
                "The will that dreads the judgment is the same will that keeps summoning " +
                "it, divided against itself, restless while the confession stays " +
                "half-finished. Let the admission stand complete and plain: fear " +
                "confessed, motive unvarnished, and the heart surrendered to rest at " +
                "last — Friday left to arrive on its own terms.",
        ),
        FewShot(
            raw = "i know i should exercise every morning but i never actually start",
            styled = "I confess it plainly: I know I should exercise every morning but I never " +
                "actually start, and memory, searched honestly, finds no morning on " +
                "which the beginning was refused only once — it was refused daily, " +
                "quietly, and loved in its refusal. As a boy I loved a fault for itself " +
                "and wanted the theft more than the fruit; so here the divided will " +
                "wants the credit of intending more than the act intended. The will that " +
                "approves the morning exercise is the same will that buries it each " +
                "dawn, and a house so divided finds no rest while the division goes " +
                "unnamed. Let the confession be finished, then: the deferral admitted " +
                "without varnish, the motive surrendered, and the first morning begun " +
                "small and whole-hearted.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PHILOSOPHERS,
)
