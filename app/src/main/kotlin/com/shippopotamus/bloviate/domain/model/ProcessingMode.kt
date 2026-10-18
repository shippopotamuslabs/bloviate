package com.shippopotamus.bloviate.domain.model

/**
 * Where dictation audio is processed. Phase 1 is on-device only; the cloud
 * slot is reserved in the plan but intentionally not built (no cloud value
 * exists yet, and no code may branch on one).
 */
enum class ProcessingMode {
    ON_DEVICE,
}
