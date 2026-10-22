package com.shippopotamus.bloviate.domain.model

/**
 * Why a recording finished on its own (ENC-160), as opposed to a manual
 * stop (the check tap) or an explicit cancel (the X tap, a password
 * focus). The reason rides the stop result into the send pipeline so the
 * Done state can name why the bubble auto-sent.
 */
enum class AutoFinishReason {

    /** Thirty seconds of sub-threshold mic audio (a left mic, a finished thought). */
    SILENCE,

    /** Ten minutes of continuous speech hit the absolute safety backstop. */
    MAX_LENGTH,
}
