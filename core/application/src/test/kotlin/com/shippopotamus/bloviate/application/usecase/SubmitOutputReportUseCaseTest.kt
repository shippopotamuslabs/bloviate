package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportReason
import com.shippopotamus.bloviate.testing.FakeLastStyledResultPort
import com.shippopotamus.bloviate.testing.FakeOutputReportPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubmitOutputReportUseCaseTest {

    private val fakeReport = FakeOutputReportPort()
    private val fakeLastResult = FakeLastStyledResultPort()

    private val useCase = SubmitOutputReportUseCase(
        reportPort = fakeReport,
        lastResultPort = fakeLastResult,
    )

    @Test
    fun `no last result submits nothing`() = runTest {
        val outcome = useCase(
            reason = OutputReportReason.WRONG,
            appVersion = "1.0 (42)",
            clientReportId = "report-1",
        )

        assertEquals(SubmitOutputReportOutcome.NoResult, outcome)
        assertTrue(fakeReport.requests.isEmpty())
    }

    @Test
    fun `the request is built from the last result plus reason, version, and id`() = runTest {
        fakeLastResult.record(
            LastStyledResult(
                styleId = "seneca",
                sourceTranscript = "the local cleaned words",
                styledOutput = "The styled words.",
                model = "model-x",
                provider = "provider-y",
                recordedAtEpochMillis = 99L,
            ),
        )
        fakeReport.outcome = OutputReportOutcome.Submitted(duplicate = true)

        val outcome = useCase(
            reason = OutputReportReason.OFFENSIVE,
            appVersion = "1.0 (42)",
            clientReportId = "report-1",
        )

        assertEquals(SubmitOutputReportOutcome.Submitted(duplicate = true), outcome)
        val request = fakeReport.requests.single()
        assertEquals("report-1", request.clientReportId)
        assertEquals("The styled words.", request.styledOutput)
        assertEquals("the local cleaned words", request.sourceTranscript)
        assertEquals(OutputReportReason.OFFENSIVE, request.reason)
        assertEquals("offensive", request.reason.wireValue)
        assertEquals("seneca", request.styleId)
        assertEquals("model-x", request.model)
        assertEquals("provider-y", request.provider)
        assertEquals("1.0 (42)", request.appVersion)
    }

    @Test
    fun `signed-out and unavailable port outcomes map through`() = runTest {
        fakeLastResult.record(
            LastStyledResult(
                styleId = "seneca",
                sourceTranscript = "words",
                styledOutput = "Words.",
                model = null,
                provider = null,
                recordedAtEpochMillis = 1L,
            ),
        )

        fakeReport.outcome = OutputReportOutcome.SignedOut
        assertEquals(
            SubmitOutputReportOutcome.SignedOut,
            useCase(OutputReportReason.OTHER, "1.0 (42)", "report-1"),
        )

        fakeReport.outcome = OutputReportOutcome.Unavailable
        assertEquals(
            SubmitOutputReportOutcome.Unavailable,
            useCase(OutputReportReason.OTHER, "1.0 (42)", "report-1"),
        )
    }
}
