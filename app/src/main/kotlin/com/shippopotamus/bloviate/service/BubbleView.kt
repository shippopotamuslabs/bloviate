package com.shippopotamus.bloviate.service

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.diagnostics.CadenceCounters
import com.shippopotamus.bloviate.domain.evaluator.BubbleAppearanceMath
import com.shippopotamus.bloviate.domain.evaluator.ElapsedTimeFormatter
import com.shippopotamus.bloviate.domain.evaluator.WaveformHistory
import com.shippopotamus.bloviate.domain.model.AutoFinishReason
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleRenderParams
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.presentation.BloviateColors
import com.shippopotamus.bloviate.presentation.BloviateSpacing
import com.shippopotamus.bloviate.presentation.BloviateTheme
import com.shippopotamus.bloviate.presentation.barShape
import com.shippopotamus.bloviate.presentation.bubbleShape
import com.shippopotamus.bloviate.presentation.tintColors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BUBBLE_ELEVATION: Dp = 6.dp

private val WAVEFORM_BAR_WIDTH: Dp = 4.dp
private val WAVEFORM_BAR_SPACING: Dp = 3.dp
private val WAVEFORM_MIN_BAR_HEIGHT: Dp = 4.dp

/**
 * Shimmer parameters, Enclave's `ShimmerText` values verbatim (ENC-76):
 * the erase band is 200 px wide and the sweep travels 1 000 px per
 * 1 500 ms pass. The band's strength is `1 − highlight alpha`, so the
 * highlight color is passed at [SHIMMER_HIGHLIGHT_ALPHA] to land the
 * strength mid-range.
 */
private const val SHIMMER_BAND_WIDTH_PX: Float = 200f
private const val SHIMMER_SWEEP_DISTANCE_PX: Float = 1_000f
private const val SHIMMER_SWEEP_DURATION_MS: Int = 1_500
private const val SHIMMER_HIGHLIGHT_ALPHA: Float = 0.45f
private const val DISMISS_ZONE_SCALE: Float = 0.8f
private const val FULL_SCALE: Float = 1f
private const val QUIET_BAR_ALPHA: Float = 0.35f
private const val SILENCE_THRESHOLD: Float = 0.05f

/**
 * Display windows for the transient Done/Error states, mirroring
 * [BubbleRecordingController]'s timer values. The composition reports the
 * elapse as a backstop (ENC-65): the controller's own timer normally wins,
 * and the guarded transition makes the second report a no-op.
 */
private const val DONE_DISPLAY_MS: Long = 1_200L
private const val ERROR_DISPLAY_MS: Long = 1_500L

/**
 * Widest elapsed readout the recording bar can show (ENC-162): the
 * recording's absolute backstop is ten minutes, which
 * [ElapsedTimeFormatter] renders as `10:00`. Every live value is four
 * glyphs (`m:ss`) until the final minute, so measuring this reference
 * pins the elapsed slot's width for the whole recording.
 */
private const val ELAPSED_SLOT_REFERENCE: String = "10:00"

/** Waveform width for [WaveformHistory.DEFAULT_BAR_COUNT] bars. */
private val WAVEFORM_WIDTH: Dp =
    WAVEFORM_BAR_WIDTH * WaveformHistory.DEFAULT_BAR_COUNT +
        WAVEFORM_BAR_SPACING * (WaveformHistory.DEFAULT_BAR_COUNT - 1)

/** Spacing between the separate recording-row elements (ENC-167). */
internal val RECORDING_ELEMENT_SPACING: Dp = BloviateSpacing.s8

/** Fixed width of the elapsed readout slot inside the recording pill. */
internal val ELAPSED_SLOT_WIDTH: Dp = 40.dp

/** Horizontal inset inside the waveform + elapsed pill. */
internal val RECORDING_PILL_HORIZONTAL_PADDING: Dp = BloviateSpacing.s12

/** Gap between the waveform and the elapsed readout inside the pill. */
internal val RECORDING_PILL_INNER_SPACING: Dp = BloviateSpacing.s8

/** Width of the waveform + elapsed pill, from fixed constants only (ENC-167). */
internal fun recordingPillWidthDp(): Float =
    (RECORDING_PILL_HORIZONTAL_PADDING * 2 + WAVEFORM_WIDTH +
        RECORDING_PILL_INNER_SPACING + ELAPSED_SLOT_WIDTH).value

/** Total width of the separate recording row, from fixed constants only (ENC-167). */
internal fun recordingRowWidthDp(renderParams: BubbleRenderParams): Float =
    renderParams.barSlotDp * 2 + recordingPillWidthDp() + RECORDING_ELEMENT_SPACING.value * 2

/**
 * The floating dictation bubble and the recording row it swaps to —
 * one overlay window, instant content swaps (ENC-167; states per
 * `contracts/UI_DESIGN_SPEC.md` §3):
 *
 * - [RecordingBarState.Idle]: the mic bubble. Tap starts a recording; drag
 *   repositions; drag to the bottom dismisses for the field session.
 *   While the speech engine is still warming up ([engineWarming],
 *   ENC-126) the bubble shows a spinner in place of the mic and taps are
 *   clean no-ops — the bubble appears on schedule instead of staying
 *   absent for the seconds the engine needs, which read as broken.
 * - [RecordingBarState.Recording]: separate elements in a row —
 *   an **X** bubble cancels, a pill carries a live ~10-bar waveform
 *   plus elapsed time, and a **check** bubble stops + transcribes.
 *   There is no hold-to-talk and no drag in this layout: X and check
 *   are independent tap targets (ENC-167).
 * - [RecordingBarState.Transcribing] / [RecordingBarState.Polishing]:
 *   the bar while the send pipeline runs, showing the stage's name as
 *   small shimmering text (ENC-76) — the pipeline has two stages with
 *   very different latencies, and the bar says which one it is in.
 * - [RecordingBarState.Done] / [RecordingBarState.Error]: transient
 *   bubble treatments while the pipeline finishes; not interactive.
 *
 * Gestures are detected inside the composition: the composition's internal
 * child view is the touch target, so a view-level touch listener on the
 * hosting ComposeView would starve. High-level events go to the overlay
 * service's drag controller / recording controller, which own the window
 * and the session. Waveform levels are read through the [waveformLevels]
 * lambda inside the Canvas draw block (`CODE_STYLE_RULES.md` §4.5) — the
 * level list itself never enters snapshot state.
 *
 * What paces the waveform redraw is a **frame clock inside the Recording
 * branch** (ENC-69 round 2), not recomposition: [RecordingBarState.Recording.frame]
 * bumps do recompose this content, but [RecordingBar] is skippable and
 * every one of its parameters compares equal across a frame bump —
 * `elapsedMs` only moves per recorder chunk, and the levels lambda and
 * callbacks are bound references, equal by construction — so the runtime
 * skipped it and its Canvas redrew only when the elapsed ticked (~1 Hz,
 * in lockstep with the timer; device-confirmed). Instead, [RecordingBar]
 * runs a `withFrameMillis` loop while it is composed and the Canvas reads
 * the tick in its draw block: every display frame invalidates the draw
 * phase and re-reads the fresh levels, whatever recomposition does.
 *
 * Done/Error auto-return: an effect keyed on the bar state reports the
 * display window's elapse through [onTransientStateElapsed] (ENC-65). The
 * recording controller runs the same return on its own timer; whichever
 * fires first wins, so the bubble can never strand on the check or the
 * error mark — the composition is provably alive whenever either is
 * visible (the stage shimmer animates in this same window).
 *
 * Appearance (ENC-68): [appearance] is the user's persisted look —
 * opacity on the root (so the bar states inherit it), geometry from
 * [BubbleAppearanceMath] (the bar scales with the bubble's size step),
 * silhouette and tint from the presentation mapping. The default is the
 * pre-ENC-68 shipping look, and a new emission restyles the showing
 * bubble in place — no window recreation.
 */
@Composable
fun BubbleContent(
    barState: RecordingBarState,
    waveformLevels: () -> List<Float>,
    inDismissZone: Boolean,
    onTap: () -> Unit = {},
    engineWarming: Boolean = false,
    onCancelRecording: () -> Unit = {},
    onSendRecording: () -> Unit = {},
    onTransientStateElapsed: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (deltaXPx: Float, deltaYPx: Float, timeMs: Long) -> Unit = { _, _, _ -> },
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    appearance: BubbleAppearance = BubbleAppearance.DEFAULT,
) {
    val renderParams = remember(appearance) { BubbleAppearanceMath.renderParams(appearance) }
    LaunchedEffect(barState) {
        val displayMs = when (barState) {
            is RecordingBarState.Done -> DONE_DISPLAY_MS
            RecordingBarState.Error -> ERROR_DISPLAY_MS
            RecordingBarState.Idle,
            is RecordingBarState.Recording,
            RecordingBarState.Transcribing,
            RecordingBarState.Polishing,
            -> null
        }
        if (displayMs != null) {
            delay(displayMs)
            onTransientStateElapsed()
        }
    }
    // One root for every state, sized by its content: the window is
    // WRAP_CONTENT, so this Box IS the window's size. Every state
    // change is an instant content swap (ENC-167) — no size animation
    // ever runs on this root. The user's opacity rides the root
    // (ENC-68), so every state wears it uniformly.
    Box(
        modifier = modifier
            .graphicsLayer { alpha = renderParams.opacity },
    ) {
        when (barState) {
            RecordingBarState.Idle -> MicBubble(
                inDismissZone = inDismissZone,
                engineWarming = engineWarming,
                appearance = appearance,
                renderParams = renderParams,
                onTap = onTap,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            is RecordingBarState.Recording -> RecordingBar(
                elapsedMs = barState.elapsedMs,
                waveformLevels = waveformLevels,
                inDismissZone = inDismissZone,
                appearance = appearance,
                renderParams = renderParams,
                onCancelRecording = onCancelRecording,
                onSendRecording = onSendRecording,
            )
            RecordingBarState.Transcribing,
            RecordingBarState.Polishing,
            -> ProcessingBar(
                label = stringResource(
                    requireNotNull(barState.processingLabelRes()) {
                        "Only the pipeline stages carry a stage label: $barState"
                    },
                ),
                appearance = appearance,
                renderParams = renderParams,
            )
            is RecordingBarState.Done -> DoneBubble(
                autoFinish = barState.autoFinish,
                appearance = appearance,
                renderParams = renderParams,
            )
            RecordingBarState.Error -> ErrorBubble(
                appearance = appearance,
                renderParams = renderParams,
            )
        }
    }
}

@Composable
private fun MicBubble(
    inDismissZone: Boolean,
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    engineWarming: Boolean = false,
) {
    val description = stringResource(
        if (engineWarming) R.string.bubble_warming_description else R.string.bubble_content_description,
    )
    // The dismiss zone keeps its danger treatment whatever the tint:
    // it is a warning state, not part of the user's chosen look.
    val tint = appearance.tint.tintColors()
    val containerColor = if (inDismissZone) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        tint.container
    }
    val contentColor = if (inDismissZone) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        tint.content
    }
    val scale = if (inDismissZone) DISMISS_ZONE_SCALE else FULL_SCALE
    Surface(
        modifier = modifier
            .size(renderParams.bubbleSizeDp.dp)
            .bubbleGestures(
                onTap = onTap,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = appearance.shape.bubbleShape(),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (engineWarming) {
                // The warming indication (ENC-126): a spinner where the
                // mic will be. It is the affordance — while it shows,
                // taps do nothing (the recording controller gates them).
                CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.size(renderParams.bubbleIconDp.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = Modifier.size(renderParams.bubbleIconDp.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordingBar(
    elapsedMs: Long,
    waveformLevels: () -> List<Float>,
    inDismissZone: Boolean,
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Frame-clock redraw for the waveform (ENC-69 round 2).
    val drawTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            withFrameMillis { drawTick.value += 1 }
        }
    }
    val recordingDescription = stringResource(R.string.bubble_recording_description)
    val cancelDescription = stringResource(R.string.bubble_cancel_description)
    val sendDescription = stringResource(R.string.bubble_send_description)
    val tint = appearance.tint.tintColors()
    val containerColor = if (inDismissZone) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        tint.container
    }
    val contentColor = if (inDismissZone) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        tint.content
    }
    val scale = if (inDismissZone) DISMISS_ZONE_SCALE else FULL_SCALE
    // Separate elements, instant swap (ENC-167): [X bubble]
    // [waveform + elapsed pill] [check bubble]. X and check are
    // independent tap targets (Surface onClick); there is no drag
    // gesture in this layout at all.
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = recordingDescription },
        horizontalArrangement = Arrangement.spacedBy(RECORDING_ELEMENT_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onCancelRecording,
            modifier = Modifier.size(renderParams.barSlotDp.dp),
            shape = appearance.shape.bubbleShape(),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = BUBBLE_ELEVATION,
        ) {
            Box(
                modifier = Modifier.semantics {
                    contentDescription = cancelDescription
                    role = Role.Button
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(renderParams.barIconDp.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .height(renderParams.barHeightDp.dp)
                .width(recordingPillWidthDp().dp),
            shape = appearance.shape.barShape(),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = BUBBLE_ELEVATION,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = RECORDING_PILL_HORIZONTAL_PADDING),
                horizontalArrangement = Arrangement.spacedBy(RECORDING_PILL_INNER_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaveformCanvas(
                    levels = waveformLevels,
                    drawTick = { drawTick.value },
                    barColor = contentColor,
                    modifier = Modifier
                        .width(WAVEFORM_WIDTH)
                        .height(renderParams.waveformHeightDp.dp),
                )
                Box(
                    modifier = Modifier.width(ELAPSED_SLOT_WIDTH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ELAPSED_SLOT_REFERENCE,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                    )
                    Text(
                        text = ElapsedTimeFormatter.format(elapsedMs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Surface(
            onClick = onSendRecording,
            modifier = Modifier.size(renderParams.barSlotDp.dp),
            shape = appearance.shape.bubbleShape(),
            color = if (inDismissZone) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (inDismissZone) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            shadowElevation = BUBBLE_ELEVATION,
        ) {
            Box(
                modifier = Modifier.semantics {
                    contentDescription = sendDescription
                    role = Role.Button
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(renderParams.barIconDp.dp),
                )
            }
        }
    }
}

/**
 * The stage word the processing bar shows for a send-pipeline state
 * (ENC-76): the label is state-driven — [RecordingBarState.Transcribing]
 * and [RecordingBarState.Polishing] name their stages, every other state
 * has no stage word. The word is also the bar's content description, so
 * what a screen reader announces is what the bar says.
 */
internal fun RecordingBarState.processingLabelRes(): Int? = when (this) {
    RecordingBarState.Transcribing -> R.string.bubble_stage_transcribing
    RecordingBarState.Polishing -> R.string.bubble_stage_polishing
    RecordingBarState.Idle,
    is RecordingBarState.Recording,
    is RecordingBarState.Done,
    RecordingBarState.Error,
    -> null
}

/**
 * The bar while the send pipeline runs: the current stage's name as
 * small shimmering text (ENC-76). Not interactive — the pipeline owns
 * this window.
 *
 * The pill HUGS its content (ENC-89): the processing states carry no
 * waveform and no buttons, so the recording bar's wide skeleton (two
 * slots + a weighted center) stretched a nearly full-width pill around
 * one short word. Here the width is the label plus gospel padding
 * (s24 each side, the button-grade horizontal inset). The swap into
 * this pill is instant (ENC-167). Height stays the bar's, and the word
 * stays the content description, so TalkBack still announces the
 * stage.
 */
@Composable
private fun ProcessingBar(
    label: String,
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    modifier: Modifier = Modifier,
) {
    val tint = appearance.tint.tintColors()
    Surface(
        modifier = modifier
            .height(renderParams.barHeightDp.dp)
            .semantics { contentDescription = label },
        shape = appearance.shape.barShape(),
        color = tint.container,
        contentColor = tint.content,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = BloviateSpacing.s24),
            contentAlignment = Alignment.Center,
        ) {
            ShimmerText(
                text = label,
                baseColor = tint.content,
                highlightColor =
                    MaterialTheme.colorScheme.primary.copy(alpha = SHIMMER_HIGHLIGHT_ALPHA),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * A word with a sweep running through it — ported from Enclave's
 * `ShimmerText` (`core/ui/component/ShimmerText.kt`), which its bottom
 * input bar shows while transcribing (ENC-76). The technique is
 * Enclave's: the text is drawn in [baseColor] on an offscreen layer, then
 * a gradient band sweeps across under [BlendMode.DstOut], erasing a
 * moving window out of the glyphs so the surface behind shows through.
 * The sweep is a 1 500 ms linear infinite transition (offset 0→1 000 px,
 * restart), the band 200 px wide; the band's strength is derived from the
 * highlight color's alpha (`1 − alpha`), exactly as Enclave computes it.
 * The animated offset is read inside the draw block, so the sweep runs
 * at display rate without recomposing.
 */
@Composable
private fun ShimmerText(
    text: String,
    baseColor: Color,
    highlightColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val animatedOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_SWEEP_DISTANCE_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_SWEEP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val bandStrength = (1f - highlightColor.alpha).coerceIn(0f, 1f)
    Text(
        text = text,
        style = style.copy(color = baseColor),
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val offset = animatedOffset.value
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = bandStrength),
                            Color.Transparent,
                        ),
                        start = Offset(offset - SHIMMER_BAND_WIDTH_PX, 0f),
                        end = Offset(offset, 0f),
                    )
                    drawRect(brush = brush, blendMode = BlendMode.DstOut)
                }
            },
    )
}

@Composable
private fun DoneBubble(
    autoFinish: AutoFinishReason?,
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    modifier: Modifier = Modifier,
) {
    // ENC-155/ENC-160: an auto-finished send lands on the same Done
    // check, but names why the session stopped itself — dead air, the
    // safety backstop, or (null) a manual check — so an auto-send never
    // reads as a crash.
    val description = stringResource(
        when (autoFinish) {
            AutoFinishReason.SILENCE -> R.string.bubble_done_silence_description
            AutoFinishReason.MAX_LENGTH -> R.string.bubble_done_capped_description
            null -> R.string.bubble_done_description
        },
    )
    val semantic = BloviateColors.extended.semantic
    Surface(
        modifier = modifier
            .size(renderParams.bubbleSizeDp.dp)
            .semantics { contentDescription = description },
        // State colors stay semantic (the tint is the user's look, not a
        // status signal); the silhouette follows the chosen shape.
        shape = appearance.shape.bubbleShape(),
        color = semantic.success,
        contentColor = semantic.onSuccess,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(renderParams.bubbleIconDp.dp),
            )
        }
    }
}

@Composable
private fun ErrorBubble(
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.bubble_error_description)
    val semantic = BloviateColors.extended.semantic
    Surface(
        modifier = modifier
            .size(renderParams.bubbleSizeDp.dp)
            .semantics { contentDescription = description },
        shape = appearance.shape.bubbleShape(),
        color = semantic.danger,
        contentColor = semantic.onDanger,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The waveform, scaled down from Enclave's `LiveVoiceOverlay` Canvas:
 * center-anchored rounded bars whose heights track the recorded levels.
 * Levels arrive as a deferred read and are only touched inside the draw
 * block, so a new amplitude never recomposes the bar. [drawTick] is read
 * in the draw block for the same reason in reverse: its change *does*
 * invalidate the draw phase (every display frame, via the caller's
 * frame-clock loop), so the fresh levels are drawn even when the bar's
 * composables are skipped as unchanged (ENC-69 round 2).
 */
@Composable
private fun WaveformCanvas(
    levels: () -> List<Float>,
    barColor: Color,
    modifier: Modifier = Modifier,
    drawTick: () -> Int = { 0 },
) {
    Canvas(modifier = modifier) {
        // Subscribes this draw scope to the frame tick: each frame redraws
        // and re-reads the levels below. The value itself is not drawn.
        drawTick()
        CadenceCounters.canvasDraw() // ENC-69 cadence counters (L4)
        val barWidthPx = WAVEFORM_BAR_WIDTH.toPx()
        val spacingPx = WAVEFORM_BAR_SPACING.toPx()
        val minHeightPx = WAVEFORM_MIN_BAR_HEIGHT.toPx()
        levels().forEachIndexed { index, level ->
            val heightPx = (WaveformHistory.heightFraction(level) * size.height)
                .coerceAtLeast(minHeightPx)
            val x = index * (barWidthPx + spacingPx)
            val y = (size.height - heightPx) / 2f
            drawRoundRect(
                color = if (level > SILENCE_THRESHOLD) barColor else barColor.copy(alpha = QUIET_BAR_ALPHA),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, heightPx),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f),
            )
        }
    }
}

/** Tap + drag detectors for the mic bubble, as established in ENC-59. */
private fun Modifier.bubbleGestures(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
    }
    .pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onDragStart() },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y, change.uptimeMillis)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragCancel() },
        )
    }

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Idle", showBackground = true)
@Composable
private fun BubbleContentIdlePreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Idle,
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Warming", showBackground = true)
@Composable
private fun BubbleContentWarmingPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Idle,
            waveformLevels = { emptyList() },
            inDismissZone = false,
            engineWarming = true,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Dismiss zone", showBackground = true)
@Composable
private fun BubbleContentDismissZonePreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Idle,
            waveformLevels = { emptyList() },
            inDismissZone = true,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Custom look - large cookie, amber, 60%", showBackground = true)
@Composable
private fun BubbleContentCustomLookPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Idle,
            waveformLevels = { emptyList() },
            inDismissZone = false,
            appearance = BubbleAppearance(
                opacity = 0.6f,
                size = BubbleSize.LARGE,
                shape = BubbleShape.COOKIE,
                tint = BubbleTint.WARNING,
            ),
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Recording", showBackground = true)
@Composable
private fun BubbleContentRecordingPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Recording(elapsedMs = 7_000L),
            waveformLevels = { PREVIEW_LEVELS },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Transcribing", showBackground = true)
@Composable
private fun BubbleContentTranscribingPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Transcribing,
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Polishing", showBackground = true)
@Composable
private fun BubbleContentPolishingPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Polishing,
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Done", showBackground = true)
@Composable
private fun BubbleContentDonePreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Done(),
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Done max length", showBackground = true)
@Composable
private fun BubbleContentDoneMaxLengthPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH),
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Done silence", showBackground = true)
@Composable
private fun BubbleContentDoneSilencePreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Done(autoFinish = AutoFinishReason.SILENCE),
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "Error", showBackground = true)
@Composable
private fun BubbleContentErrorPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Error,
            waveformLevels = { emptyList() },
            inDismissZone = false,
        )
    }
}

private val PREVIEW_LEVELS: List<Float> =
    listOf(0.12f, 0.38f, 0.72f, 0.95f, 0.55f, 0.24f, 0.61f, 0.83f, 0.42f, 0.18f)

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    BloviateTheme(content = content)
}
