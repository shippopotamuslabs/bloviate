package com.shippopotamus.bloviate.domain.evaluator

import javax.inject.Inject

/**
 * The on-device mechanical style transform (ENC-72): `lowercase casual`.
 * Pure and total — lowercases every letter, then drops exactly one trailing
 * period at the very end when present (relaxed punctuation). Everything
 * else — `?`, `!`, interior periods, whitespace — passes through untouched;
 * collapsing is the baseline's job, which already ran before this.
 */
class LowercaseCasualTransformer @Inject constructor() {

    /** Applies the lowercase-casual transform to already-cleaned [text]. */
    fun transform(text: String): String {
        val lowered = text.lowercase()
        return if (lowered.endsWith(".")) lowered.dropLast(1) else lowered
    }
}
