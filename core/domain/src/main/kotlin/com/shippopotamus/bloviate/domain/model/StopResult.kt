package com.shippopotamus.bloviate.domain.model

/**
 * The stopped samples of one finished recording (ENC-160). [autoFinish]
 * is null for a manual stop (the check tap collected the samples) and
 * set when the session finished on its own — thirty seconds of dead air
 * ([AutoFinishReason.SILENCE]) or the ten-minute safety backstop
 * ([AutoFinishReason.MAX_LENGTH]).
 */
data class StopResult(
    val samples: FloatArray,
    val autoFinish: AutoFinishReason? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StopResult) return false
        return samples.contentEquals(other.samples) && autoFinish == other.autoFinish
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + (autoFinish?.hashCode() ?: 0)
}
