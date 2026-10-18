package com.shippopotamus.bloviate.service

import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.testing.FakeTextInsertionPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InsertionTranscriptSinkTest {

    private val fakeInsertion = FakeTextInsertionPort()
    private val sink = InsertionTranscriptSink(fakeInsertion)

    @Test
    fun `delivers the transcript to insertion and reports its outcome`() = runTest {
        val result = sink.onTranscript("hello world")

        assertEquals(listOf("hello world"), fakeInsertion.insertedTexts)
        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
    }

    @Test
    fun `a refusal propagates to the caller instead of being swallowed`() = runTest {
        fakeInsertion.nextResult =
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD)

        val result = sink.onTranscript("hello world")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD),
            result,
        )
    }
}
