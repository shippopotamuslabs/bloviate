package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted writing-style choice (ENC-72). The cleanup pipeline
 * resolves the style from this preference at send time — an edit in the
 * Style tab applies to the very next dictation, nothing captures the
 * selection at startup.
 *
 * The selection is a style id string (see `StyleCatalog`); unknown ids read
 * as the catalog default, so a removed style can never wedge the pipeline.
 */
interface StylePreferencePort {

    /** The currently selected style id; the catalog default until changed. */
    val selectedStyleId: StateFlow<String>

    /** Persists [styleId] as the selected writing style. */
    suspend fun selectStyle(styleId: String)
}
