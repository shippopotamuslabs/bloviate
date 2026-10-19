package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.StyleCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StylePromptBuilderTest {

    private val builder = StylePromptBuilder()
    private val professional = StyleCatalog.byId("professional")

    @Test
    fun `system message carries the style instructions plus the only-text rule`() {
        val prompt = builder.build(style = professional, transcript = "hello there")

        assertEquals("system", prompt.system.role)
        assertTrue(prompt.system.content.contains(professional.systemInstructions))
        assertTrue(prompt.system.content.contains("ONLY"))
    }

    @Test
    fun `user message carries every few-shot pair then the transcript`() {
        val prompt = builder.build(style = professional, transcript = "meet me at noon")

        assertEquals("user", prompt.user.role)
        professional.fewShots.forEach { shot ->
            assertTrue(prompt.user.content.contains(shot.raw))
            assertTrue(prompt.user.content.contains(shot.styled))
        }
        assertTrue(prompt.user.content.contains("meet me at noon"))
        // The transcript comes last — the examples ground the voice first.
        val lastShot = professional.fewShots.last().styled
        assertTrue(prompt.user.content.indexOf("meet me at noon") > prompt.user.content.indexOf(lastShot))
    }

    @Test
    fun `prompt carries no field or app context`() {
        val prompt = builder.build(style = professional, transcript = "plain words")

        val combined = prompt.system.content + "\n" + prompt.user.content
        assertTrue("package" !in combined.lowercase())
        assertTrue("password" !in combined.lowercase())
        assertTrue("application" !in combined.lowercase())
    }
}
