package com.shippopotamus.bloviate.domain.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CadenceCountersTest {

    @Test
    fun `summary is not due before the interval elapses`() {
        CadenceCounters.begin(nowMs = 10_000L)
        repeat(3) { CadenceCounters.tickerPublish() }

        assertNull(CadenceCounters.summaryLineIfDue(nowMs = 10_999L))
    }

    @Test
    fun `summary reports the window counts and drains them`() {
        CadenceCounters.begin(nowMs = 0L)
        repeat(29) { CadenceCounters.tickerPublish() }
        repeat(29) { CadenceCounters.sessionReceipt() }
        repeat(29) { CadenceCounters.barPublish() }
        repeat(29) { CadenceCounters.snapshotWrite() }
        CadenceCounters.canvasDraw()

        assertEquals(
            "cadence L1=29 L2r=29 L2p=29 L3=29 L4=1",
            CadenceCounters.summaryLineIfDue(nowMs = 1_000L),
        )
        // The window drained: a second due call reports zeros, not a repeat.
        assertEquals(
            "cadence L1=0 L2r=0 L2p=0 L3=0 L4=0",
            CadenceCounters.summaryLineIfDue(nowMs = 2_000L),
        )
    }

    @Test
    fun `totals accumulate across summary windows`() {
        CadenceCounters.begin(nowMs = 0L)
        repeat(10) { CadenceCounters.tickerPublish() }
        CadenceCounters.summaryLineIfDue(nowMs = 1_000L)
        repeat(5) { CadenceCounters.tickerPublish() }
        repeat(2) { CadenceCounters.canvasDraw() }

        assertEquals(
            "cadence totals L1=15 L2r=0 L2p=0 L3=0 L4=2",
            CadenceCounters.totalsLine(),
        )
    }

    @Test
    fun `begin resets the previous recording's counts`() {
        CadenceCounters.begin(nowMs = 0L)
        repeat(7) { CadenceCounters.tickerPublish() }

        CadenceCounters.begin(nowMs = 5_000L)

        assertEquals(
            "cadence totals L1=0 L2r=0 L2p=0 L3=0 L4=0",
            CadenceCounters.totalsLine(),
        )
        assertNull(CadenceCounters.summaryLineIfDue(nowMs = 5_500L))
    }

    @Test
    fun `no summaries are emitted after the recording ends`() {
        CadenceCounters.begin(nowMs = 0L)
        CadenceCounters.tickerPublish()
        CadenceCounters.end()

        assertNull(CadenceCounters.summaryLineIfDue(nowMs = 60_000L))
    }
}
