package com.shippopotamus.bloviate.domain.model

/**
 * The first-run wizard's steps, in walk order (ENC-67): welcome, then
 * the four setup gates in the order a user must clear them (the
 * watcher spots fields, the overlay lets the bubble draw, the
 * microphone hears, a model transcribes), and finally the guided test
 * dictation. The gate order mirrors the Setup screen's blocker order
 * so the wizard and the dashboard can never disagree about what comes
 * first.
 */
enum class OnboardingStep {
    WELCOME,
    WATCHER,
    OVERLAY,
    MICROPHONE,
    MODEL,
    TEST,
    ;

    /** The step after this one, or null at the end of the walk. */
    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null at the start of the walk. */
    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)
}

/**
 * The user's persisted place in the first-run wizard (ENC-67): the
 * step to resume at, and whether the walk is [finished] — completed
 * or explicitly skipped. An unfinished progress is what makes the
 * wizard open on launch; finishing (or skipping) is the only way it
 * stops opening, and it can always be re-run from Setup, which resets
 * the record to [NOT_STARTED]. Persisted as a versioned DataStore
 * record (see `DataStoreOnboardingStore`).
 */
data class OnboardingProgress(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val finished: Boolean = false,
) {
    companion object {
        /** No record on disk yet: the walk has never started and is not finished. */
        val NOT_STARTED: OnboardingProgress = OnboardingProgress()
    }
}
