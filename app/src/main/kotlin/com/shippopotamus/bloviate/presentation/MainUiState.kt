package com.shippopotamus.bloviate.presentation

/** Presentation-only classification of the focused field, mapped from the domain state. */
enum class FocusClassification {
    NONE,
    EDITABLE,
    PASSWORD,
}

/** Presentation-only mapping of the user's selected dictation engine. */
enum class DictationEngineUi {
    WHISPER,
    PARAKEET,
}

/**
 * The fallback in effect on the status screen: the user [selected] one
 * engine, but its model is not Ready yet, so dictation is currently
 * running on the [effective] engine (ENC-54 default flip). Null in the UI
 * state whenever the selected engine is the one in use — or when neither
 * model is Ready and there is no fallback to report.
 */
data class EngineFallbackUi(
    val selected: DictationEngineUi,
    val effective: DictationEngineUi,
)

/** Presentation-only mapping of a transcription model's lifecycle state. */
sealed interface ModelUiStatus {

    /** No model file on the device yet. */
    data object Absent : ModelUiStatus

    /** Download in flight; [percent] is 0..100. */
    data class Downloading(val percent: Int) : ModelUiStatus

    /** Model downloaded and verified; dictation is available. */
    data object Ready : ModelUiStatus

    /** The last download attempt failed; [reason] is shown verbatim. */
    data class Failed(val reason: String) : ModelUiStatus
}

/**
 * Home/status screen state: watcher enablement, the live field-focus
 * classification, bubble overlay permission + service state, microphone
 * permission, and both engines' model lifecycles + the engine selection.
 * Carries no domain types and never any field content.
 */
data class MainUiState(
    val watcherEnabled: Boolean = false,
    val focusClassification: FocusClassification = FocusClassification.NONE,
    val focusPackageName: String? = null,
    val overlayPermissionGranted: Boolean = false,
    val bubbleServiceRunning: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val whisperModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val parakeetModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val selectedEngine: DictationEngineUi = DictationEngineUi.PARAKEET,
    val engineFallback: EngineFallbackUi? = null,
)
