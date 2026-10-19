package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LowercaseCasualTransformerTest {

    private val transformer = LowercaseCasualTransformer()

    @Test
    fun `every letter is lowercased`() {
        assertEquals(
            "hey i was thinking we could grab lunch tomorrow",
            transformer.transform("Hey I Was Thinking We Could Grab Lunch Tomorrow."),
        )
    }

    @Test
    fun `exactly one trailing period is dropped`() {
        assertEquals("hello world", transformer.transform("Hello World."))
    }

    @Test
    fun `a double trailing period keeps one`() {
        assertEquals("wait what.", transformer.transform("Wait What.."))
    }

    @Test
    fun `question and exclamation marks are kept`() {
        assertEquals("really? wow!", transformer.transform("Really? Wow!"))
    }

    @Test
    fun `interior periods are kept`() {
        assertEquals("e.g. this stays", transformer.transform("E.g. This Stays"))
    }

    @Test
    fun `text without a trailing period is only lowercased`() {
        assertEquals("no period here", transformer.transform("No Period Here"))
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", transformer.transform(""))
        assertEquals("   ", transformer.transform("   "))
    }
}
