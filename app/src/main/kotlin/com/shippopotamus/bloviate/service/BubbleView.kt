package com.shippopotamus.bloviate.service

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.diagnostics.CadenceCounters
import com.shippopotamus.bloviate.domain.evaluator.BubbleAppearanceMath
import com.shippopotamus.bloviate.domain.evaluator.ElapsedTimeFormatter
import com.shippopotamus.bloviate.domain.evaluator.WaveformHistory
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
import kotlin.math.hypot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BUBBLE_ELEVATION: Dp = 6.dp

/** The bar's inner horizontal inset is the gospel's micro spacing (ENC-86). */
private val BAR_HORIZONTAL_PADDING: Dp = BloviateSpacing.s4
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

/** Waveform width for [WaveformHistory.DEFAULT_BAR_COUNT] bars. */
private val WAVEFORM_WIDTH: Dp =
    WAVEFORM_BAR_WIDTH * WaveformHistory.DEFAULT_BAR_COUNT +
        WAVEFORM_BAR_SPACING * (WaveformHistory.DEFAULT_BAR_COUNT - 1)

/**
 * The calm width-morph spring between bar states (ENC-89): a state
 * change swaps the whole content (bubble ↔ recording bar ↔ the hugged
 * processing pill), and the root's `animateContentSize` settles the
 * window to the new width on this spring — no bounce, low stiffness,
 * calm by design (the Compose Gospel's spring motion, §6.6). Each
 * state's own geometry is exactly what it was; only the transition
 * animates. Since ENC-93 this spring governs the SETTLE transitions
 * (into the processing pill, the transient bubbles, and back to
 * idle); the recording-start expansion has its own spec below.
 */
private val BAR_MORPH_SPRING = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * The recording-start expansion spring (ENC-93): tapping the bubble
 * must SNAP the bar out to its full width — inside half a second, not
 * on ENC-89's calm settle, which the expansion originally inherited
 * (Sean's acceptance test: "it expands very, very slowly... it should
 * snap out to the full pill"). High stiffness (the gospel's
 * high-stiffness spring language, §6.2), still no bounce: the spring's
 * displacement envelope is under 1% by ~250 ms, and a shorter
 * animation also means fewer per-frame window relayouts in the
 * overlay. Only the transition INTO [RecordingBarState.Recording]
 * uses it — see [RecordingBarState.sizeAnimationSpec].
 */
private val BAR_EXPANSION_SPRING = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

/**
 * The size-animation spec for a transition INTO this state (ENC-93).
 * Motion is per-transition, not per-root: the recording-start
 * expansion snaps on [BAR_EXPANSION_SPRING]; every other state
 * change — the Listening → Processing settle ENC-89 made calm, the
 * transient Done/Error bubbles, the return to idle — keeps
 * [BAR_MORPH_SPRING]. `BubbleContent` reads this off the incoming
 * state on every recomposition, and `animateContentSize` animates a
 * size change with the spec in its current composition, so the spec
 * that governs a transition is the destination state's.
 */
internal fun RecordingBarState.sizeAnimationSpec(): FiniteAnimationSpec<IntSize> =
    when (this) {
        is RecordingBarState.Recording -> BAR_EXPANSION_SPRING
        RecordingBarState.Idle,
        RecordingBarState.Transcribing,
        RecordingBarState.Polishing,
        is RecordingBarState.Done,
        RecordingBarState.Error,
        -> BAR_MORPH_SPRING
    }

/**
 * The floating dictation bubble and the recording bar it transforms into —
 * one overlay window, two shapes (Sean's ENC-48 design, states per
 * `contracts/UI_DESIGN_SPEC.md` §3):
 *
 * - [RecordingBarState.Idle]: the mic bubble. Tap starts a recording; drag
 *   repositions; drag to the bottom dismisses for the field session.
 *   While the speech engine is still warming up ([engineWarming],
 *   ENC-126) the bubble shows a spinner in place of the mic and taps are
 *   clean no-ops — the bubble appears on schedule instead of staying
 *   absent for the seconds the engine needs, which read as broken.
 * - [RecordingBarState.Recording]: the bar — **X** cancels, a live
 *   ~10-bar waveform plus elapsed time in the middle, and the **check**
 *   stops + transcribes. There is no hold-to-talk. The bar drags (by its
 *   body) exactly like the bubble; hit-testing in the bar's gesture
 *   handler decides tap-vs-drag by movement, so a press that moves is a
 *   drag and never a button tap.
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
    // WRAP_CONTENT, so this Box IS the window's size, and its
    // animateContentSize springs the width between state geometries.
    // The spec is per-transition (ENC-93): the incoming state picks
    // it — the recording expansion snaps, the processing settle and
    // the transient bubbles stay on ENC-89's calm spring. The user's
    // opacity rides the same root (ENC-68), so every state — bubble,
    // bar, transient — wears it uniformly.
    Box(
        modifier = modifier
            .graphicsLayer { alpha = renderParams.opacity }
            .animateContentSize(animationSpec = barState.sizeAnimationSpec()),
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
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
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
                capped = barState.capped,
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
    onDragStart: () -> Unit,
    onDrag: (Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Frame-clock redraw for the waveform (ENC-69 round 2): this loop runs
    // only while the Recording branch is composed, and the waveform Canvas
    // reads the tick inside its draw block, so the wave redraws at display
    // rate from the live levels — draw-phase only, no recomposition, and
    // the gesture handlers below never see it.
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
    Surface(
        modifier = modifier
            .height(renderParams.barHeightDp.dp)
            .recordingBarGestures(
                slotSize = renderParams.barSlotDp.dp,
                onCancelTap = onCancelRecording,
                onSendTap = onSendRecording,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = recordingDescription },
        shape = appearance.shape.barShape(),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = BUBBLE_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BAR_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(renderParams.barSlotDp.dp)
                    .semantics {
                        contentDescription = cancelDescription
                        role = Role.Button
                        onClick {
                            onCancelRecording()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(renderParams.barIconDp.dp),
                )
            }
            WaveformCanvas(
                levels = waveformLevels,
                drawTick = { drawTick.value },
                barColor = contentColor,
                modifier = Modifier
                    .width(WAVEFORM_WIDTH)
                    .height(renderParams.waveformHeightDp.dp),
            )
            Text(
                text = ElapsedTimeFormatter.format(elapsedMs),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = BAR_HORIZONTAL_PADDING),
            )
            Box(
                modifier = Modifier
                    .size(renderParams.barSlotDp.dp)
                    .semantics {
                        contentDescription = sendDescription
                        role = Role.Button
                        onClick {
                            onSendRecording()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(renderParams.sendCircleDp.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(renderParams.sendIconDp.dp),
                    )
                }
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
 * (s24 each side, the button-grade horizontal inset); the root's calm
 * [BAR_MORPH_SPRING] animates the settle from the recording bar into
 * this pill (ENC-93 keeps that direction calm — only the expansion
 * out of the bubble snaps). Height stays the bar's, and the word
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
    capped: Boolean,
    appearance: BubbleAppearance,
    renderParams: BubbleRenderParams,
    modifier: Modifier = Modifier,
) {
    // ENC-155: a cap auto-send lands on the same Done check, but names
    // the max length so the stop never reads as a crash.
    val description = stringResource(
        if (capped) R.string.bubble_done_capped_description else R.string.bubble_done_description,
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

/**
 * Gesture arbitration for the recording bar. One pointer stream decides:
 * released within the touch slop → a tap, hit-tested against the cancel
 * slot (left [slotSize]) and the send slot (right [slotSize]); moved past
 * the slop → a drag ([BubbleDragSession] semantics, the same drag the
 * bubble uses) and never a button tap. Doing the arbitration in one
 * handler is what keeps the bar's buttons and its drag from fighting
 * over the stream the way sibling detectors on parent/child would. The
 * slot width is a parameter (ENC-68) because the user's size step scales
 * the bar — the hit test must measure the slots that are actually drawn.
 */
private fun Modifier.recordingBarGestures(
    slotSize: Dp,
    onCancelTap: () -> Unit,
    onSendTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(slotSize) {
    val slotWidthPx = slotSize.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        var finished = false
        var totalDeltaX = 0f
        var totalDeltaY = 0f
        var lastPosition = down.position
        while (!finished) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.previousPressed && !change.pressed) {
                finished = true
                finishBarGesture(
                    wasCancelled = event.type != PointerEventType.Release,
                    wasDragging = dragging,
                    downX = down.position.x,
                    widthPx = size.width.toFloat(),
                    slotWidthPx = slotWidthPx,
                    onCancelTap = onCancelTap,
                    onSendTap = onSendTap,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            } else {
                val delta = change.position - lastPosition
                lastPosition = change.position
                totalDeltaX += delta.x
                totalDeltaY += delta.y
                if (!dragging && hypot(totalDeltaX, totalDeltaY) > viewConfiguration.touchSlop) {
                    dragging = true
                    onDragStart()
                    onDrag(totalDeltaX, totalDeltaY, change.uptimeMillis)
                } else if (dragging && delta != Offset.Zero) {
                    change.consume()
                    onDrag(delta.x, delta.y, change.uptimeMillis)
                }
            }
        }
    }
}

@Suppress("LongParameterList") // One-shot gesture verdict: every input is needed exactly once.
private fun finishBarGesture(
    wasCancelled: Boolean,
    wasDragging: Boolean,
    downX: Float,
    widthPx: Float,
    slotWidthPx: Float,
    onCancelTap: () -> Unit,
    onSendTap: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    when {
        wasCancelled -> if (wasDragging) onDragCancel()
        wasDragging -> onDragEnd()
        downX < slotWidthPx -> onCancelTap()
        downX > widthPx - slotWidthPx -> onSendTap()
        else -> Unit // A tap on the waveform/elapsed area does nothing.
    }
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
@Preview(name = "Done capped", showBackground = true)
@Composable
private fun BubbleContentDoneCappedPreview() {
    PreviewTheme {
        BubbleContent(
            barState = RecordingBarState.Done(capped = true),
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
