package com.shippopotamus.bloviate.domain.evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextSplicerTest {

    private val splicer = TextSplicer()

    @Test
    fun `inserts at a collapsed cursor and leaves the caret after the insertion`() {
        val result = splicer.splice("hello world", selectionStart = 5, selectionEnd = 5, insertion = " big")

        assertEquals("hello big world", result.text)
        assertEquals(9, result.selectionStart)
        assertEquals(9, result.selectionEnd)
    }

    @Test
    fun `replaces the selected range`() {
        val result = splicer.splice("hello world", selectionStart = 6, selectionEnd = 11, insertion = "there")

        assertEquals("hello there", result.text)
        assertEquals(11, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun `appends at the end of the text`() {
        val result = splicer.splice("hello", selectionStart = 5, selectionEnd = 5, insertion = "!")

        assertEquals("hello!", result.text)
        assertEquals(6, result.selectionStart)
    }

    @Test
    fun `inserts into an empty field`() {
        val result = splicer.splice("", selectionStart = 0, selectionEnd = 0, insertion = "hi")

        assertEquals("hi", result.text)
        assertEquals(2, result.selectionStart)
    }

    @Test
    fun `an unreported selection (-1) appends instead of throwing`() {
        val result = splicer.splice("hello", selectionStart = -1, selectionEnd = -1, insertion = "!")

        assertEquals("hello!", result.text)
        assertEquals(6, result.selectionStart)
    }

    @Test
    fun `an out-of-range selection appends instead of throwing`() {
        val result = splicer.splice("hello", selectionStart = 99, selectionEnd = 99, insertion = "!")

        assertEquals("hello!", result.text)
        assertEquals(6, result.selectionStart)
    }

    @Test
    fun `an inverted selection appends instead of throwing`() {
        val result = splicer.splice("hello", selectionStart = 4, selectionEnd = 2, insertion = "!")

        assertEquals("hello!", result.text)
        assertEquals(6, result.selectionStart)
    }

    @Test
    fun `a hint-showing field with unreported selection yields exactly the insertion`() {
        val result = splicer.splice(
            "Ask Google",
            selectionStart = -1,
            selectionEnd = -1,
            insertion = "hello",
            hintText = "Ask Google",
            isShowingHintText = true,
        )

        assertEquals("hello", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun `a hint-showing field with selection inside the hint yields exactly the insertion`() {
        val result = splicer.splice(
            "Ask Google",
            selectionStart = 3,
            selectionEnd = 3,
            insertion = "hello",
            hintText = "Ask Google",
            isShowingHintText = true,
        )

        assertEquals("hello", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun `text equal to the hint without the flag yields exactly the insertion`() {
        val result = splicer.splice(
            "Ask Google",
            selectionStart = -1,
            selectionEnd = -1,
            insertion = "hello",
            hintText = "Ask Google",
        )

        assertEquals("hello", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun `real text with a hint present splices normally`() {
        val result = splicer.splice(
            "hello world",
            selectionStart = 5,
            selectionEnd = 5,
            insertion = " big",
            hintText = "Ask Google",
        )

        assertEquals("hello big world", result.text)
        assertEquals(9, result.selectionStart)
        assertEquals(9, result.selectionEnd)
    }

    @Test
    fun `a null hint changes nothing`() {
        val result = splicer.splice(
            "Ask Google",
            selectionStart = -1,
            selectionEnd = -1,
            insertion = "hello",
            hintText = null,
        )

        assertEquals("Ask Googlehello", result.text)
        assertEquals(15, result.selectionStart)
    }

    @Test
    fun `an empty hint changes nothing`() {
        val result = splicer.splice(
            "Ask Google",
            selectionStart = -1,
            selectionEnd = -1,
            insertion = "hello",
            hintText = "",
        )

        assertEquals("Ask Googlehello", result.text)
        assertEquals(15, result.selectionStart)
    }
}
