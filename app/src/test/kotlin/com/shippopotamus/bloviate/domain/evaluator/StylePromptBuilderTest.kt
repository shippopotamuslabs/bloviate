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
    fun `every style's system message carries the speaker invariant`() {
        // ENC-92: dictation is the speaker's own words, never a reply
        // addressed back at them — the invariant rides every style's
        // system message, personas included.
        StyleCatalog.ALL.forEach { style ->
            val prompt = builder.build(style = style, transcript = "i noticed the bug this morning")

            assertTrue(
                prompt.system.content.contains("NEVER address the speaker"),
                "${style.id} system message lacks the speaker invariant",
            )
            assertTrue(
                prompt.system.content.contains("speaker's own"),
                "${style.id} system message lacks the own-words clause",
            )
        }
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
