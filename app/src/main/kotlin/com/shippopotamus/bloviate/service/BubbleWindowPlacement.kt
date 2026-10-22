package com.shippopotamus.bloviate.service

import android.util.Log
import android.view.View
import android.view.WindowManager
import com.shippopotamus.bloviate.domain.evaluator.BubblePositionMath
import com.shippopotamus.bloviate.domain.model.BubblePixelPosition
import com.shippopotamus.bloviate.domain.model.BubblePosition
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.ScreenBounds

/**
 * Window placement for the bubble overlay: bar-form morphing (ENC-65),
 * drag-end routing (ENC-123), and resting clamps across posture changes.
 *
 * Single-writer rule (ENC-154): while the drag controller reports an
 * active drag ([isDragging]), the drag controller owns the window's
 * layout exclusively — [applyBarPlacement] and [reclampBubble] no-op,
 * and [onDragEnded] runs exactly one placement pass when the drag ends.
 * Drag clamping itself reads the drag-start size inside
 * [BubbleDragController], never the live view, so the bar's ~30 Hz
 * width churn mid-drag cannot move the clamp target under the finger.
 */
class BubbleWindowPlacement(
    private val pushLayout: (View, WindowManager.LayoutParams) -> Unit,
    private val bubbleSizePx: () -> Pair<Int, Int>,
    private val barEdgeMarginPx: () -> Int,
    private val savePosition: (BubblePosition) -> Unit,
    private val isDragging: () -> Boolean = { false },
) {

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    /**
     * The persisted anchor's in-memory mirror: collected for the
     * service's whole lifetime and updated synchronously wherever a
     * position is persisted, so re-deriving the resting position never
     * lags the store.
     */
    var restingPosition: BubblePosition = BubblePosition.DEFAULT

    // Bar-form window placement (ENC-65): the recording bar is far wider
    // than the bubble and the window is anchored top-left, so the morph
    // repositions the window to keep the whole bar on-screen. These fields
    // remember the bubble's own anchor while the bar is up; the bar's
    // placement is never persisted as the bubble's position.
    private var previousBarState: RecordingBarState = RecordingBarState.Idle
    private var barFormAnchorXPx: Int? = null
    private var barFormAnchorWidthPx: Int = 0
    private var barFormAnchorHeightPx: Int = 0
    private var barFormMoved: Boolean = false

    fun attach(view: View, params: WindowManager.LayoutParams) {
        this.view = view
        this.params = params
    }

    fun detach() {
        view = null
        params = null
        barFormAnchorXPx = null
        barFormMoved = false
        previousBarState = RecordingBarState.Idle
    }

    /**
     * Drag-end routing: a normal bubble drag persists its end position.
     * A drag that ends while the recording bar is up must NOT persist the
     * position the drag controller computed — it normalized against the
     * bar's width, and the bar's placement is not the bubble's position.
     * Instead the drag is remembered and the bubble's anchor is re-derived
     * (and persisted) when the bar closes, in [exitBarForm].
     *
     * Then exactly one placement pass runs (ENC-154): the bar form
     * re-clamps from the dragged position, the resting bubble re-derives
     * from its new anchor (a no-op push when already correct).
     */
    fun onDragEnded(position: BubblePosition, bounds: ScreenBounds) {
        if (barFormAnchorXPx != null) {
            barFormMoved = true
        } else {
            restingPosition = position
            savePosition(position)
        }
        if (barFormAnchorXPx != null) {
            applyBarPlacement(bounds)
        } else {
            reclampBubble(bounds)
        }
    }

    /** Tracks bar-state transitions for window placement (see [enterBarForm] / [exitBarForm]). */
    fun onBarStateChanged(newState: RecordingBarState, bounds: ScreenBounds) {
        val previous = previousBarState
        previousBarState = newState
        when {
            previous == RecordingBarState.Idle && newState is RecordingBarState.Recording ->
                enterBarForm(bounds)
            newState == RecordingBarState.Idle && previous != RecordingBarState.Idle ->
                exitBarForm(bounds)
        }
    }

    /**
     * The window's own layout changed size. Outside a drag, the bar form
     * re-clamps on width changes and the resting bubble re-derives on any
     * size change; mid-drag this is a no-op (ENC-154).
     */
    fun onWindowResized(widthChanged: Boolean, heightChanged: Boolean, bounds: ScreenBounds) {
        if (isDragging()) return
        if (widthChanged) applyBarPlacement(bounds)
        if ((widthChanged || heightChanged) && barFormAnchorXPx == null) reclampBubble(bounds)
    }

    /** Rotation / fold-unfold with the same window: re-clamp into the new bounds. */
    fun onConfigurationChanged(bounds: ScreenBounds) {
        reclampBubble(bounds)
    }

    /**
     * The bubble is morphing into the recording bar: capture the bubble's
     * own anchor (position + size) before the bar lays out, then place the
     * window so the bar grows toward the screen interior (ENC-65).
     */
    private fun enterBarForm(bounds: ScreenBounds) {
        val view = view ?: return
        val params = params ?: return
        barFormAnchorXPx = params.x
        barFormAnchorWidthPx = view.width
        barFormAnchorHeightPx = view.height
        barFormMoved = false
        applyBarPlacement(bounds)
    }

    /**
     * The bar is closing back to the mic bubble: the window returns to the
     * bubble's own anchor. If the bar was dragged, the bubble takes the
     * bar's final left edge (clamped to the bubble's own travel range) as
     * its new anchor, and that — computed with the bubble's size, never
     * the bar's — is what gets persisted.
     */
    private fun exitBarForm(bounds: ScreenBounds) {
        val anchorXPx = barFormAnchorXPx
        barFormAnchorXPx = null
        val moved = barFormMoved
        barFormMoved = false
        val view = view ?: return
        val params = params ?: return
        if (anchorXPx == null) return
        if (moved) {
            val landed = BubblePositionMath.clamp(
                topLeft = BubblePixelPosition(xPx = params.x, yPx = params.y),
                bubbleWidthPx = barFormAnchorWidthPx,
                bubbleHeightPx = barFormAnchorHeightPx,
                bounds = bounds,
            )
            params.x = landed.xPx
            params.y = landed.yPx
            val landedPosition = BubblePositionMath.toNormalized(
                topLeft = landed,
                bubbleWidthPx = barFormAnchorWidthPx,
                bubbleHeightPx = barFormAnchorHeightPx,
                bounds = bounds,
            )
            restingPosition = landedPosition
            savePosition(landedPosition)
        } else {
            params.x = anchorXPx
        }
        pushLayout(view, params)
    }

    /**
     * (Re)place the window so the current bar form sits fully on-screen:
     * [BubblePositionMath.barWindowX] from the bubble's anchor — or, once
     * the user has dragged the bar, from its dragged position, so a width
     * change re-clamps instead of snapping the bar back to the anchor.
     * Runs when the bar form is entered and whenever the window's width
     * changes while the bar is up. No-op while dragging (ENC-154).
     */
    private fun applyBarPlacement(bounds: ScreenBounds) {
        if (isDragging()) return
        val anchorXPx = barFormAnchorXPx ?: return
        val view = view ?: return
        val params = params ?: return
        if (view.width <= 0) return
        val baseXPx = if (barFormMoved) params.x else anchorXPx
        val targetXPx = BubblePositionMath.barWindowX(
            bubbleXPx = baseXPx,
            barWidthPx = view.width,
            bounds = bounds,
            edgeMarginPx = barEdgeMarginPx(),
        )
        if (targetXPx != params.x) {
            params.x = targetXPx
            pushLayout(view, params)
        }
    }

    /**
     * Keep the resting bubble on-screen when its size or the bounds change
     * around it (ENC-123).
     *
     * Outside bar form the resting position is RE-DERIVED from the
     * persisted anchor against the bubble's own appearance size — never
     * pixel-clamped against the view's live size. The old clamp read the
     * view mid-morph: on collapse the view is still bar-wide for the whole
     * settle animation, so each frame's clamp pulled a docked bubble's x
     * left toward the bar's width, and the one-way clamp never let it
     * back — the bubble came to rest displaced toward mid-screen after
     * every record cycle. The same clamp stranded a bubble off its docked
     * edge when an ENC-68 size step shrank it. Re-derivation is a
     * constant during a morph (the anchor and the bubble's size do not
     * change), preserves the docked edge at every size step, and moves
     * mid-screen positions only as the anchor model prescribes.
     *
     * In bar form the window's placement belongs to [applyBarPlacement] /
     * [exitBarForm]; the clamp below only keeps the bar itself on-screen
     * across a posture change. No-op while dragging (ENC-154).
     */
    private fun reclampBubble(bounds: ScreenBounds) {
        if (isDragging()) return
        val view = view ?: return
        val params = params ?: return
        val target = if (barFormAnchorXPx != null) {
            BubblePositionMath.clamp(
                topLeft = BubblePixelPosition(xPx = params.x, yPx = params.y),
                bubbleWidthPx = view.width,
                bubbleHeightPx = view.height,
                bounds = bounds,
            )
        } else {
            val (bubbleWidthPx, bubbleHeightPx) = bubbleSizePx()
            BubblePositionMath.restingTopLeft(
                position = restingPosition,
                bubbleWidthPx = bubbleWidthPx,
                bubbleHeightPx = bubbleHeightPx,
                bounds = bounds,
            )
        }
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
