package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * One user-created custom style in the account-owned cache (ENC-199).
 *
 * [revision] is server-sync metadata for a later cloud backup slice;
 * local-only records carry `0`. [updatedAtEpochMillis] orders conflicts
 * and drives "last edited" surfaces.
 */
data class CustomStyleRecord(
    val definition: StyleDefinition,
    val revision: Long,
    val updatedAtEpochMillis: Long,
)
