package com.shippopotamus.bloviate.domain.model

/**
 * Outcome of one attempt to insert a finished transcript into the focused
 * field (`contracts/ARCHITECTURE_RULES.md` §5.3). The recording bar shows
 * Done only for [Inserted]; [Refused] and [Failed] surface the Error state
 * and leave the user's existing text untouched.
 */
sealed interface InsertionResult {

    /** The text landed in the field via [method]. */
    data class Inserted(val method: Method) : InsertionResult {

        enum class Method {

            /** Clipboard + `ACTION_PASTE` — the default; respects the caret/selection. */
            PASTE,

            /** `ACTION_SET_TEXT` splice at the cursor — fallback only, when paste is unavailable. */
            SET_TEXT_SPLICE,
        }
    }

    /** Insertion was not attempted (or was refused) for a structural [reason]. */
    data class Refused(val reason: Reason) : InsertionResult {

        enum class Reason {

            /** The focused field is a password field — never insert, ever. */
            PASSWORD_FIELD,

            /** No editable field currently holds input focus. */
            NO_EDITABLE_FIELD,

            /** The accessibility watcher is not connected, so there is no node to act on. */
            WATCHER_UNAVAILABLE,
        }
    }

    /**
     * Insertion was attempted and failed. [detail] is a log-safe summary
     * (never field content) for diagnostics.
     */
    data class Failed(val detail: String) : InsertionResult
}
