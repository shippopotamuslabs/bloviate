package com.shippopotamus.bloviate.domain.diagnostics

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Temporary publish/draw counters for the ENC-69 waveform-cadence
 * investigation (round 2): the state chain from the session manager to the
 * overlay Canvas reads correct at every hop, yet the on-device waveform
 * steps at ~1 Hz in lockstep with the elapsed timer. These counters count
 * one recording at four layer boundaries so a single logcat pull names the
 * layer where the ~30 Hz stream dies:
 *
 * - **L1** — [RecordingSessionManager]'s level ticker published a level.
 * - **L2r / L2p** — [BubbleRecordingController] received a session state /
 *   published a bar state.
 * - **L3** — [BubbleOverlayService] wrote the bar-state snapshot the
 *   composition reads.
 * - **L4** — the waveform Canvas draw block actually executed.
 *
 * Pure and framework-free (JDK atomics only): increments are lock-free and
 * behavior-neutral, safe on the capture, main, and draw threads. The
 * controller asks [summaryLineIfDue] for a per-second line while a
 * recording runs and logs [totalsLine] when it ends; both are formatted
 * here so every layer reports in one shape under the `BubbleOverlay` tag.
 *
 * Removal: delete this file and the `CadenceCounters.` call sites (one per
 * layer, each commented ENC-69) once the cadence bug is closed.
 */
object CadenceCounters {

    /** One layer's counters: a per-summary window plus the recording's totals. */
    private class Layer {
        val window = AtomicInteger(0)
        val total = AtomicInteger(0)

        fun bump() {
            window.incrementAndGet()
            total.incrementAndGet()
        }

        fun drainWindow(): Int = window.getAndSet(0)

        fun reset() {
            window.set(0)
            total.set(0)
        }
    }

    private val tickerPublishes = Layer()
    private val sessionReceipts = Layer()
    private val barPublishes = Layer()
    private val snapshotWrites = Layer()
    private val canvasDraws = Layer()

    private val lastSummaryMs = AtomicLong(0L)

    @Volatile
    private var recordingActive: Boolean = false

    /** Start counting a new recording; all windows and totals reset. */
    fun begin(nowMs: Long) {
        tickerPublishes.reset()
        sessionReceipts.reset()
        barPublishes.reset()
        snapshotWrites.reset()
        canvasDraws.reset()
        lastSummaryMs.set(nowMs)
        recordingActive = true
    }

    /** Stop counting; [totalsLine] stays readable until the next [begin]. */
    fun end() {
        recordingActive = false
    }

    /** L1: the session manager's level ticker published one amplitude level. */
    fun tickerPublish() = tickerPublishes.bump()

    /** L2r: the recording controller received one session state. */
    fun sessionReceipt() = sessionReceipts.bump()

    /** L2p: the recording controller published one bar state. */
    fun barPublish() = barPublishes.bump()

    /** L3: the overlay service wrote one bar-state snapshot for the composition. */
    fun snapshotWrite() = snapshotWrites.bump()

    /** L4: the waveform Canvas draw block executed once. */
    fun canvasDraw() = canvasDraws.bump()

    /**
     * The per-second summary line once [SUMMARY_INTERVAL_MS] has elapsed
     * since the previous one (or since [begin]); null when not due, when a
     * concurrent caller won the slot, or when no recording is active. Each
     * due call drains the window counters, so the numbers are per-second
     * rates: `cadence L1=29 L2r=29 L2p=29 L3=29 L4=1`.
     */
    fun summaryLineIfDue(nowMs: Long): String? {
        if (!recordingActive) return null
        val last = lastSummaryMs.get()
        if (nowMs - last < SUMMARY_INTERVAL_MS) return null
        if (!lastSummaryMs.compareAndSet(last, nowMs)) return null
        return "cadence" +
            " L1=${tickerPublishes.drainWindow()}" +
            " L2r=${sessionReceipts.drainWindow()}" +
            " L2p=${barPublishes.drainWindow()}" +
            " L3=${snapshotWrites.drainWindow()}" +
            " L4=${canvasDraws.drainWindow()}"
    }

    /** The whole recording's counts, logged when the recording ends. */
    fun totalsLine(): String =
        "cadence totals" +
            " L1=${tickerPublishes.total.get()}" +
            " L2r=${sessionReceipts.total.get()}" +
            " L2p=${barPublishes.total.get()}" +
            " L3=${snapshotWrites.total.get()}" +
            " L4=${canvasDraws.total.get()}"

    /** Cadence summaries are emitted at most once per second while recording. */
    const val SUMMARY_INTERVAL_MS: Long = 1_000L
}
