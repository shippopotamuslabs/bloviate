package com.shippopotamus.bloviate.presentation

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint

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

/** The first thing standing between the user and dictation, in gate order (ENC-75). */
enum class SetupBlocker {
    WATCHER,

    /** The watcher is enabled in settings but its service is not connected (ENC-129 supervision). */
    WATCHER_DISCONNECTED,
    OVERLAY,
    MICROPHONE,
    MODEL,
    BUBBLE,
}

/**
 * The Setup header pill's verdict (ENC-75): either everything dictation
 * needs is in place, or the first blocker in gate order. Derived from
 * [MainUiState] by [setupStatus] — a pure function of view state, so
 * the pill can never disagree with the rows below it.
 */
sealed interface SetupStatusUi {

    /** Watcher, overlay, microphone, a ready model, and the bubble are all in place. */
    data object Ready : SetupStatusUi

    /** Dictation is blocked; [blocker] is the first gate that fails. */
    data class Blocked(val blocker: SetupBlocker) : SetupStatusUi
}

/**
 * Derives the header-pill verdict from this state. Gate order is the
 * order a user must fix things in: the watcher (without it no field is
 * ever spotted), its connection (an enabled-but-disconnected watcher
 * spots nothing either, ENC-129), the overlay permission (without it
 * the bubble cannot draw), the microphone, a downloaded model (either
 * engine counts — dictation works on whichever is ready), and finally
 * the bubble switch itself, which is a tap rather than a setup errand.
 */
fun MainUiState.setupStatus(): SetupStatusUi =
    when {
        !watcherEnabled -> SetupStatusUi.Blocked(SetupBlocker.WATCHER)
        !watcherConnected -> SetupStatusUi.Blocked(SetupBlocker.WATCHER_DISCONNECTED)
        !overlayPermissionGranted -> SetupStatusUi.Blocked(SetupBlocker.OVERLAY)
        !micPermissionGranted -> SetupStatusUi.Blocked(SetupBlocker.MICROPHONE)
        whisperModelStatus !is ModelUiStatus.Ready &&
            parakeetModelStatus !is ModelUiStatus.Ready ->
            SetupStatusUi.Blocked(SetupBlocker.MODEL)
        !bubbleServiceRunning -> SetupStatusUi.Blocked(SetupBlocker.BUBBLE)
        else -> SetupStatusUi.Ready
    }

/** Presentation-only mapping of the bubble size steps (ENC-68). */
enum class BubbleSizeUi {
    SMALL,
    MEDIUM,
    LARGE,
}

/** Presentation-only mapping of the bubble silhouettes (ENC-68; circle merged into pill in ENC-122). */
enum class BubbleShapeUi {
    PILL,
    SQUIRCLE,
    COOKIE,
    CLOVER,
    ARCH,
}

/** Presentation-only mapping of the bubble tints, under their user-facing names (ENC-68). */
enum class BubbleTintUi {
    INDIGO,
    NEUTRAL,
    GREEN,
    AMBER,
    ROSE,
    SKY,
}

/**
 * The Setup screen's bubble-look state (ENC-68): the user's opacity,
 * size, silhouette, and tint, mapped from the domain [BubbleAppearance]
 * by the ViewModel so no domain type reaches a composable.
 */
data class BubbleAppearanceUi(
    val opacity: Float = 1f,
    val size: BubbleSizeUi = BubbleSizeUi.MEDIUM,
    val shape: BubbleShapeUi = BubbleShapeUi.PILL,
    val tint: BubbleTintUi = BubbleTintUi.INDIGO,
)

internal fun BubbleSizeUi.toDomain(): BubbleSize =
    when (this) {
        BubbleSizeUi.SMALL -> BubbleSize.SMALL
        BubbleSizeUi.MEDIUM -> BubbleSize.MEDIUM
        BubbleSizeUi.LARGE -> BubbleSize.LARGE
    }

internal fun BubbleSize.toUi(): BubbleSizeUi =
    when (this) {
        BubbleSize.SMALL -> BubbleSizeUi.SMALL
        BubbleSize.MEDIUM -> BubbleSizeUi.MEDIUM
        BubbleSize.LARGE -> BubbleSizeUi.LARGE
    }

internal fun BubbleShapeUi.toDomain(): BubbleShape =
    when (this) {
        BubbleShapeUi.PILL -> BubbleShape.PILL
        BubbleShapeUi.SQUIRCLE -> BubbleShape.SQUIRCLE
        BubbleShapeUi.COOKIE -> BubbleShape.COOKIE
        BubbleShapeUi.CLOVER -> BubbleShape.CLOVER
        BubbleShapeUi.ARCH -> BubbleShape.ARCH
    }

internal fun BubbleShape.toUi(): BubbleShapeUi =
    when (this) {
        BubbleShape.PILL -> BubbleShapeUi.PILL
        BubbleShape.SQUIRCLE -> BubbleShapeUi.SQUIRCLE
        BubbleShape.COOKIE -> BubbleShapeUi.COOKIE
        BubbleShape.CLOVER -> BubbleShapeUi.CLOVER
        BubbleShape.ARCH -> BubbleShapeUi.ARCH
    }

internal fun BubbleTintUi.toDomain(): BubbleTint =
    when (this) {
        BubbleTintUi.INDIGO -> BubbleTint.INDIGO
        BubbleTintUi.NEUTRAL -> BubbleTint.NEUTRAL
        BubbleTintUi.GREEN -> BubbleTint.SUCCESS
        BubbleTintUi.AMBER -> BubbleTint.WARNING
        BubbleTintUi.ROSE -> BubbleTint.DANGER
        BubbleTintUi.SKY -> BubbleTint.INFO
    }

internal fun BubbleTint.toUi(): BubbleTintUi =
    when (this) {
        BubbleTint.INDIGO -> BubbleTintUi.INDIGO
        BubbleTint.NEUTRAL -> BubbleTintUi.NEUTRAL
        BubbleTint.SUCCESS -> BubbleTintUi.GREEN
        BubbleTint.WARNING -> BubbleTintUi.AMBER
        BubbleTint.DANGER -> BubbleTintUi.ROSE
        BubbleTint.INFO -> BubbleTintUi.SKY
    }

internal fun BubbleAppearance.toUi(): BubbleAppearanceUi =
    BubbleAppearanceUi(
        opacity = opacity,
        size = size.toUi(),
        shape = shape.toUi(),
        tint = tint.toUi(),
    )

/**
 * Home/status screen state: watcher enablement + connection, bubble
 * overlay permission + service state, microphone permission, and both
 * engines' model lifecycles + the engine selection. Carries no domain
 * types and never any field content. (The live field-focus state is
 * deliberately absent, ENC-142: the bubble pipeline consumes it
 * directly; Setup has no honest use for it.)
 */
data class MainUiState(
    val watcherEnabled: Boolean = false,

    /** Whether the watcher service is connected right now (ENC-129 supervision). */
    val watcherConnected: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val bubbleServiceRunning: Boolean = false,

    /** Whether battery optimization leaves Bloviate alone (ENC-129). */
    val batteryExempt: Boolean = false,

    /** Whether the bubble service may start itself at boot and restart after a kill (ENC-129). */
    val autoStartEnabled: Boolean = true,
    val micPermissionGranted: Boolean = false,
    val whisperModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val parakeetModelStatus: ModelUiStatus = ModelUiStatus.Absent,
    val selectedEngine: DictationEngineUi = DictationEngineUi.PARAKEET,
    val engineFallback: EngineFallbackUi? = null,
    val bubbleAppearance: BubbleAppearanceUi = BubbleAppearanceUi(),
)
