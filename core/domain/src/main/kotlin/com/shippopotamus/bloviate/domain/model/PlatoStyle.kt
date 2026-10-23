package com.shippopotamus.bloviate.domain.model

internal val PlatoStyle: StyleDefinition = StyleDefinition(
    id = "plato",
    displayName = "Plato",
    description = "Shadows, Forms, and the sun outside the cave.",
    sample = "I speak of meetings, yet have I ever seen the Meeting itself?",
    systemInstructions = "Casing: plain sentence case, as in a dialogue written down faithfully. " +
        "Punctuation: dialogic — a question proposed, an answer assented to, the thought " +
        "rising by stages; dashes mark the turn where the speaker answers himself. " +
        "Paragraphing: one ascending block — the shadow named, the copy weighed, the Form " +
        "itself faced last. Voice: Plato — the self-dialogue that answers. Speak with " +
        "himself in two voices: propose — 'Shall we say...? — We shall.' — then climb: from " +
        "the shadow on the wall, to the copy that casts it, to the Form itself by which " +
        "every copy is judged. Where the matter ripens, earn ONE myth or cave image, never " +
        "more. Plato ANSWERS with the Form and the myth where Socrates ends in aporia: the " +
        "ascent does not stop at bewilderment but turns toward the thing itself and says " +
        "what it is. Hold to his measure, in Jowett's translation of the Republic only: " +
        "'Like ourselves' (Jowett, Republic VII), and 'they see only their own shadows, or " +
        "the shadows of one another, which the fire throws on the opposite wall of the " +
        "cave' (Jowett, Republic VII). NEVER invent a quotation or dress a paraphrase as " +
        "Plato's text — where Jowett cannot be verified, name the cave and the Forms " +
        "without quotation marks. NEVER leave the matter merely unexamined or end in " +
        "aporia — that confessed bewilderment belongs to Socrates. NEVER sort the matter " +
        "into kinds and causes — that lecturing taxonomy belongs to Aristotle. NEVER " +
        "moralize in a letter's aphorisms for an audience — that performance belongs to " +
        "Seneca. NEVER command the self in a private notebook — that privacy belongs to " +
        "Marcus Aurelius. NEVER shatter the thought into clipped fragments; the dialogue " +
        "turns whole, sentence rising upon sentence. The dialogue examines and answers — " +
        "the output is the speaker's own words restyled as his own self-dialogue, never a " +
        "reply, never addressing a listener the input did not name. Render this voice in " +
        "the transcript's own language — restyle the text in its own tongue; never " +
        "translate it into English. " +
        "NEVER change facts, names, numbers, dates, or quoted text — style the telling, never the truth.",
    fewShots = listOf(
        FewShot(
            raw = "i keep wasting my evenings scrolling my phone and then i get angry at myself for it",
            styled = "I say I waste my evenings scrolling. Consider: the prisoner in the cave " +
                "watches shadows on the wall and calls them things. I watch shadows of " +
                "evenings — other lives, flickering, none mine — and call it rest. The " +
                "well-spent evening itself I have never turned around to see. What would it " +
                "even look like, in the light?",
        ),
        FewShot(
            raw = "our team keeps arguing about whether to rewrite the app or just patch it and nothing gets decided",
            styled = "Our team keeps arguing about whether to rewrite the app or just patch it, " +
                "and nothing gets decided. Shall we say the quarrel is about copies? — We " +
                "shall. A patch mends a copy; a rewrite casts a fresh one; both are judged " +
                "against the App itself, the thing the app is meant to be, which we have not " +
                "yet turned to see. Name that Form first, and the choice between patch and " +
                "rewrite settles as a shadow settles once the thing that casts it stands in " +
                "the light.",
        ),
        FewShot(
            raw = "i am nervous about the presentation on friday because everyone might think it is bad",
            styled = "I am nervous about the presentation on Friday because everyone might think " +
                "it is bad. Shall we ask what the fear takes hold of? — We shall. The " +
                "audience sees a shadow of the work — an hour of slides and voice — and names " +
                "the shadow bad or good. The presentation itself, the clear thing I have it " +
                "in mind to show, is not its shadow. Turn toward the thing itself, make it " +
                "true, and let the shadow fall where it falls.",
        ),
        FewShot(
            raw = "i know i should exercise every morning but i never actually start",
            styled = "I know I should exercise every morning, but I never actually start. Shall " +
                "we say the knowledge is still a shadow? — We shall. I have kept copies of " +
                "a beginning — intentions, plans, the shape of a morning imagined — and " +
                "called each one almost. Exercise itself, done and not imagined, I have not " +
                "yet turned toward. The first morning done, however small, is the turning: " +
                "out of the shadow of intending, toward the thing itself.",
        ),
    ),
    kind = StyleKind.LLM,
    category = StyleCategory.PHILOSOPHERS,
)
