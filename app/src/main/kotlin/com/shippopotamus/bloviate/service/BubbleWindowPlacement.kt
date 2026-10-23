package com.shippopotamus.bloviate.service

import android.util.Log
import android.view.View
import android.view.WindowManager
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.ScreenBounds

/**
 * Window placement for the bubble overlay (ENC-167): capture-anchor →
 * one-shot place → restore-on-exit for bar-form states, drag-end routing
 * and resting clamps otherwise.
 *
 * Bar form (Recording / Transcribing / Polishing) is placed ONCE per
 * state entry, before its content composes, from the known content
 * width supplied by [barContentWidthPx] — never from a measured live
 * view width. The side and window x come from
 * [BubblePositionMath.barWindowXOneShot]: the anchor edge sitting at
 * the bubble's spot does not move across Recording → Processing →
 * Done. There is no per-frame correction: [onWindowResized] is a no-op
 * for position while a bar form is up, and the recording row carries
 * no drag, so pinning is structural (the ENC-162 pin gates are retired
 * with the morph).
 *
 * Single-writer rule (ENC-154) still holds outside bar form: while the
 * drag controller reports an active drag ([isDragging]) it owns the
 * window's layout exclusively and [onDragEnded] runs exactly one
 * placement pass when the drag ends.
 */
class BubbleWindowPlacement(
    private val pushLayout: (View, WindowManager.LayoutParams) -> Unit,
    private val bubbleSizePx: () -> Pair<Int, Int>,
    private val barEdgeMarginPx: () -> Int,
    private val savePosition: (BubblePosition) -> Unit,
    private val isDragging: () -> Boolean = { false },
    private val barContentWidthPx: (RecordingBarState) -> Int = { 0 },
) {

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    /** The persisted anchor's in-memory mirror; see [restingPosition] consumers. */
    var restingPosition: BubblePosition = BubblePosition.DEFAULT

    private var previousBarState: RecordingBarState = RecordingBarState.Idle
    private var barFormAnchorXPx: Int? = null
    private var barFormAnchorYPx: Int = 0
    private var barFormAnchorWidthPx: Int = 0

    fun attach(view: View, params: WindowManager.LayoutParams) {
        this.view = view
        this.params = params
    }

    fun detach() {
        view = null
        params = null
        barFormAnchorXPx = null
        previousBarState = RecordingBarState.Idle
    }

    /**
     * Drag-end routing: a normal (idle) bubble drag persists its end
     * position. A drag end reported while a bar form is up cannot be a
     * recording-row drag (the row has none, ENC-167), so it persists
     * nothing and moves nothing. Then exactly one placement pass runs
     * (ENC-154) for the resting bubble.
     */
    fun onDragEnded(position: BubblePosition, bounds: ScreenBounds) {
        if (barFormAnchorXPx != null) return
        restingPosition = position
        savePosition(position)
        reclampBubble(bounds)
    }

    /** Tracks bar-state transitions for one-shot window placement. */
    fun onBarStateChanged(newState: RecordingBarState, bounds: ScreenBounds) {
        val previous = previousBarState
        previousBarState = newState
        val wasBarForm = previous.isBarForm()
        val isBarForm = newState.isBarForm()
        when {
            !wasBarForm && isBarForm -> enterBarForm(newState, bounds)
            wasBarForm && isBarForm && previous::class != newState::class ->
                placeBarForm(newState, bounds)
            wasBarForm && !isBarForm -> exitBarForm()
            else -> Unit
        }
    }

    /**
     * The window's own layout changed size. While a bar form is up this
     * is a no-op for position (ENC-167): bar placement is one-shot and
     * already computed from known widths, so a resize must never pull
     * the window. Outside bar form, a size change re-derives the resting
     * position; mid-drag this is a no-op (ENC-154).
     */
    fun onWindowResized(widthChanged: Boolean, heightChanged: Boolean, bounds: ScreenBounds) {
        if (isDragging()) return
        if (barFormAnchorXPx != null) return
        if (widthChanged || heightChanged) reclampBubble(bounds)
    }

    /** Rotation / fold-unfold with the same window: re-place into the new bounds. */
    fun onConfigurationChanged(bounds: ScreenBounds) {
        if (barFormAnchorXPx != null) {
            // Re-derive the anchor in the new bounds, then re-place once.
            val (bubbleWidthPx, bubbleHeightPx) = bubbleSizePx()
            val anchor = BubblePositionMath.restingTopLeft(
                position = restingPosition,
                bubbleWidthPx = bubbleWidthPx,
                bubbleHeightPx = bubbleHeightPx,
                bounds = bounds,
            )
            barFormAnchorXPx = anchor.xPx
            barFormAnchorYPx = anchor.yPx
            barFormAnchorWidthPx = bubbleWidthPx
            placeBarForm(previousBarState, bounds, pushEvenIfSameY = true)
        } else {
            reclampBubble(bounds)
        }
    }

    private fun enterBarForm(newState: RecordingBarState, bounds: ScreenBounds) {
        val params = params ?: return
        val (bubbleWidthPx, _) = bubbleSizePx()
        barFormAnchorXPx = params.x
        barFormAnchorYPx = params.y
        barFormAnchorWidthPx = bubbleWidthPx
        placeBarForm(newState, bounds)
    }

    private fun placeBarForm(
        state: RecordingBarState,
        bounds: ScreenBounds,
        pushEvenIfSameY: Boolean = false,
    ) {
        if (isDragging()) return
        val anchorXPx = barFormAnchorXPx ?: return
        val view = view ?: return
        val params = params ?: return
        val contentWidthPx = barContentWidthPx(state)
        if (contentWidthPx <= 0) return
        val targetXPx = BubblePositionMath.barWindowXOneShot(
            anchorXPx = anchorXPx,
            anchorWidthPx = barFormAnchorWidthPx,
            contentWidthPx = contentWidthPx,
            bounds = bounds,
            edgeMarginPx = barEdgeMarginPx(),
        )
        if (targetXPx != params.x || pushEvenIfSameY) {
            params.x = targetXPx
            params.y = barFormAnchorYPx
            pushLayout(view, params)
        }
    }

    /** The bar form closes: the window returns to the bubble's own anchor spot. */
    private fun exitBarForm() {
        val anchorXPx = barFormAnchorXPx
        barFormAnchorXPx = null
        val view = view ?: return
        val params = params ?: return
        if (anchorXPx == null) return
        params.x = anchorXPx
        params.y = barFormAnchorYPx
        pushLayout(view, params)
    }

    private fun RecordingBarState.isBarForm(): Boolean =
        this is RecordingBarState.Recording ||
            this == RecordingBarState.Transcribing ||
            this == RecordingBarState.Polishing

    /**
     * Keep the resting bubble on-screen when its size or the bounds change
     * around it (ENC-123): re-derived from the persisted anchor against
     * the bubble's own appearance size — never pixel-clamped against a
     * live view size. No-op while dragging (ENC-154) and while a bar
     * form owns placement.
     */
    private fun reclampBubble(bounds: ScreenBounds) {
        if (isDragging()) return
        if (barFormAnchorXPx != null) return
        val view = view ?: return
        val params = params ?: return
        val (bubbleWidthPx, bubbleHeightPx) = bubbleSizePx()
        val target = BubblePositionMath.restingTopLeft(
            position = restingPosition,
            bubbleWidthPx = bubbleWidthPx,
            bubbleHeightPx = bubbleHeightPx,
            bounds = bounds,
        )
        if (target.xPx != params.x || target.yPx != params.y) {
            params.x = target.xPx
            params.y = target.yPx
            pushLayout(view, params)
        }
        Log.d(TAG, "Bubble re-clamped into ${bounds.widthPx}x${bounds.heightPx}")
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
    }
}
