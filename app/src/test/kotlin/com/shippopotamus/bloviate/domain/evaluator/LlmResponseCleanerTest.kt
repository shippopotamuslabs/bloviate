package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlmResponseCleanerTest {

    private val cleaner = LlmResponseCleaner()

    @Test
    fun `plain output passes through untouched`() {
        assertEquals("Following up tomorrow.", cleaner.clean("Following up tomorrow."))
    }

    @Test
    fun `fenced output loses its fences`() {
        assertEquals(
            "Following up tomorrow.",
            cleaner.clean("```\nFollowing up tomorrow.\n```"),
        )
    }

    @Test
    fun `a fenced block with a language tag loses the whole fence`() {
        assertEquals(
            "Following up tomorrow.",
            cleaner.clean("```text\nFollowing up tomorrow.\n```"),
        )
    }

    @Test
    fun `a preamble line is stripped and the answer kept`() {
        assertEquals(
            "Following up tomorrow.",
            cleaner.clean("Here is the rewritten text:\nFollowing up tomorrow."),
        )
    }

    @Test
    fun `a long first line is the answer, not a preamble`() {
        val longLine = "This opening line runs well past any preamble length because it keeps going and going onward."
        assertEquals(longLine, cleaner.clean(longLine))
    }

    @Test
    fun `blank output stays blank so the caller falls back`() {
        assertEquals("", cleaner.clean("   \n  "))
        assertEquals("", cleaner.clean("```\n```"))
    }
}
