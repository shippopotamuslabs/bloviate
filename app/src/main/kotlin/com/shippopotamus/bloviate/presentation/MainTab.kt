package com.shippopotamus.bloviate.presentation

/**
 * The app shell's three top-level tabs (ENC-71). Tab selection is transient
 * UI state: the shell holds it in the composition via `rememberSaveable`
 * (which survives rotation and process recreation through the saved-state
 * registry) and it is deliberately NOT persisted to DataStore — persisted
 * formats are schema (AGENTS.md §3.4) and a tab choice is not schema.
 */
enum class MainTab {
    SETUP,
    STYLE,
    DICTIONARY,
    ;

    /** The stable string form written into the saved-state registry. */
    fun toSavedValue(): String = name

    companion object {
        /** Shown on first launch, and whenever a saved value cannot be read back. */
        val DEFAULT: MainTab = SETUP

        /** Restores a tab from [toSavedValue]; null or unknown values fall back to [DEFAULT]. */
        fun fromSavedValue(value: String?): MainTab =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
