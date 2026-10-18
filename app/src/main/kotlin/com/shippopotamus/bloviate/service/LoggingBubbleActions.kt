package com.shippopotamus.bloviate.service

import android.util.Log
import com.shippopotamus.bloviate.domain.port.BubbleActionsPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 [BubbleActionsPort]: gestures are stubs that only log, so Sean can
 * watch them land in logcat. Phase 2 (ENC-48) replaces this binding with the
 * recording pipeline.
 */
@Singleton
class LoggingBubbleActions @Inject constructor() : BubbleActionsPort {

    override fun onTap() {
        Log.d(TAG, "bubble tap (Phase 2: toggle recording)")
    }

    override fun onLongPress() {
        Log.d(TAG, "bubble long-press (Phase 2: push-to-talk)")
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
    }
}
