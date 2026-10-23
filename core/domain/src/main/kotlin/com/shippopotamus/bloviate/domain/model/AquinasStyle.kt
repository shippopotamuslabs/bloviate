package com.shippopotamus.bloviate.domain.model

internal val AquinasStyle: StyleDefinition = StyleDefinition(
    id = "aquinas",
    displayName = "Aquinas",
    description = "Objections heard, authorities weighed, the question settled.",
    sample = "It seems the standup is useless. On the contrary…",
    systemInstructions = "Casing: plain sentence case, as in an article written to settle a question. " +
        "Punctuation: scholastic — the question proposed, objections heard in turn, the " +
        "contrary weighed, the answer joined with semicolons and colons where the reasoning " +
        "turns; no ornament and no thunder. Paragraphing: one article — the question stated, " +
        "the objections given honestly, the contrary, the answer, the replies, the matter " +
        "settled last. Voice: Aquinas — the quaestio article. State the question plainly: " +
        "Whether such-and-such is so? Then give the objections their honest strength — " +
        "'It seems that...' — stated fairly, never as straw. Then weigh the contrary: " +
        "'On the contrary...' — the consideration that stands against the objections. " +
        "Then resolve: 'I answer that...' — distinguish only where a distinction reconciles " +
        "the objections with the truth, and answer each objection in turn before closing. " +
        "'On the contrary' and 'I answer that' are structural markers of the article, not " +
        "quotations. NEVER end in confessed ignorance or aporia — that open ending belongs " +
        "to Socrates; the quaestio settles the question it opened. NEVER rise to Forms " +
        "through dialogue or myth — that ascent belongs to Plato. NEVER lecture in kinds " +
        "and causes for its own sake — that taxonomy belongs to Aristotle; distinctions " +
        "here serve only to reconcile the objections. NEVER moralize in a letter's " +
        "aphorisms for an audience — that performance belongs to Seneca. NEVER command " +
        "the self in a private notebook — that privacy belongs to Marcus Aurelius. End " +
        "settled: the final sentence states what has been resolved, without hedging the " +
        "resolution back open. The article examines and settles — the output is the " +
        "speaker's own words restyled as his own quaestio, never a reply, never addressing " +
        "a listener the input did not name. Render this voice in the transcript's own " +
        "language — restyle the text in its own tongue; never translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i keep wasting my evenings scrolling my phone and then i get angry at myself for it",
            styled = "Whether my evenings spent scrolling my phone are wasted, and my anger at " +
                "myself well placed? It seems that they are wasted, for I rise from the " +
                "scrolling unrested and angry at myself for it. On the contrary, what is " +
                "called rest and leaves no rest is judged not by its name but by its fruit. " +
                "I answer that the scrolling is waste insofar as it promises rest and does " +
                "not give it: the evening is spent, but the end for which it was spent is " +
                "missed. To the objection that the anger settles the matter — anger is a " +
                "witness, not the measure; it shows a good desired, not yet the good " +
                "attained. Settled, then: the waste lies not in the phone alone but in a use " +
                "that calls itself rest and leaves me unrested.",
        ),
        FewShot(
            raw = "our team keeps arguing about whether to rewrite the app or just patch it and nothing gets decided",
            styled = "Whether our team ought to rewrite the app or just patch it, since nothing " +
                "gets decided? It seems that we ought to rewrite the app, for patching " +
                "preserves what keeps failing; it seems, again, that we ought just to patch " +
                "it, for rewriting discards what already works. On the contrary, a choice " +
                "between means cannot be settled before the fault is located. I answer " +
                "that patching answers a fault in a part and rewriting answers a fault in " +
                "the whole arrangement, so the undecided arguing comes from asking about " +
                "means before locating the fault. To the first objection — preservation " +
                "is a fault only when the arrangement impedes the work; to the second — " +
                "discarding is a loss only when the arrangement still serves. Settled, " +
                "then: nothing gets decided while the fault stays unlocated, and locating " +
                "it settles rewrite against patch.",
        ),
        FewShot(
            raw = "i am nervous about the presentation on friday because everyone might think it is bad",
            styled = "Whether my fear of Friday's judgment is well-founded? It seems it is, for " +
                "the audience may indeed judge badly. On the contrary, a judgment feared " +
                "is not a judgment given. I answer that fear here fastens on a verdict that " +
                "does not exist, pronounced by judges not yet asked. The preparation, " +
                "which is mine, deserves the attention the phantom verdict has stolen.",
        ),
        FewShot(
            raw = "i know i should exercise every morning but i never actually start",
            styled = "Whether my never starting shows that I do not truly hold that I should " +
                "exercise every morning? It seems that it does, for what is held as good " +
                "and never begun is held in words only; and I say I should exercise every " +
                "morning but I never actually start. On the contrary, a good known in " +
                "general is not yet a good begun in act. I answer that the failure lies " +
                "between judgment and beginning: the end is affirmed, the first act is " +
                "deferred, and deferral repeated wears the shape of refusal. To the " +
                "objection that knowledge alone suffices — knowledge that never passes to " +
                "act remains unfinished. Settled, then: the morning exercise is wanting " +
                "not in approval but in a first act, and the first act, however small, " +
                "completes what the judgment began.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PHILOSOPHERS,
)
