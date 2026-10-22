package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BaselineTextPostProcessorTest {

    private val processor = BaselineTextPostProcessor()

    @Test
    fun `capitalizes the first letter of the utterance`() {
        assertEquals("Hello world", processor.process("hello world"))
    }

    @Test
    fun `strips a leading filler and capitalizes what follows`() {
        assertEquals("I think so", processor.process("Um, I think so"))
        assertEquals("Hello world", processor.process("um hello world"))
    }

    @Test
    fun `strips fillers mid-sentence`() {
        assertEquals("I went to the, store", processor.process("i went to the, uh, store"))
        assertEquals("It was, fine", processor.process("it was, er, fine"))
    }

    @Test
    fun `fillers are whole words only - words containing them survive`() {
        assertEquals("The umbrella is red", processor.process("the umbrella is red"))
        assertEquals("Run the errand, upper shelf", processor.process("run the errand, upper shelf"))
    }

    @Test
    fun `repairs sentence case after sentence-ending punctuation`() {
        assertEquals(
            "Hello world. This is fine",
            processor.process("hello world. this is fine"),
        )
        assertEquals("Really? Yes! Ok then", processor.process("really? yes! ok then"))
    }

    @Test
    fun `a decimal point does not start a new sentence`() {
        assertEquals(
            "The meeting is at 3.30 pm",
            processor.process("the meeting is at 3.30 pm"),
        )
    }

    @Test
    fun `collapses whitespace and closes up space before punctuation`() {
        assertEquals("Hello world", processor.process("  hello   world  "))
        assertEquals("Hello, world", processor.process("hello , world"))
    }

    @Test
    fun `already clean text is untouched apart from sentence case`() {
        assertEquals("Hello world.", processor.process("Hello world."))
        assertEquals("I am DONE yelling", processor.process("i am DONE yelling"))
    }

    @Test
    fun `a filler-only transcript cleans to blank`() {
        assertEquals("", processor.process("um uh er"))
        assertEquals("", processor.process("Um, uh..."))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", processor.process(""))
        assertEquals("", processor.process("   "))
    }
}
