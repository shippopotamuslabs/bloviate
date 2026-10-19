package com.shippopotamus.bloviate.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleCatalogTest {

    @Test
    fun `the catalog holds exactly nine styles`() {
        assertEquals(9, StyleCatalog.ALL.size)
    }

    @Test
    fun `every style id is unique`() {
        val ids = StyleCatalog.ALL.map { style -> style.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every style has non-blank display fields and sample`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.displayName.isNotBlank(), "${style.id} displayName is blank")
            assertTrue(style.description.isNotBlank(), "${style.id} description is blank")
            assertTrue(style.sample.isNotBlank(), "${style.id} sample is blank")
        }
    }

    @Test
    fun `every style has non-blank instructions containing a never-change clause`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.systemInstructions.isNotBlank(), "${style.id} instructions are blank")
            assertTrue(
                style.systemInstructions.lowercase().contains("never"),
                "${style.id} instructions carry no NEVER-change clause",
            )
        }
    }

    @Test
    fun `every style has at least two few-shots with non-blank sides`() {
        StyleCatalog.ALL.forEach { style ->
            assertTrue(style.fewShots.size >= 2, "${style.id} has fewer than two few-shots")
            style.fewShots.forEach { fewShot ->
                assertTrue(fewShot.raw.isNotBlank(), "${style.id} has a blank few-shot raw side")
                assertTrue(fewShot.styled.isNotBlank(), "${style.id} has a blank few-shot styled side")
            }
        }
    }

    @Test
    fun `exactly the two casual styles are mechanical`() {
        val mechanicalIds = StyleCatalog.ALL
            .filter { style -> style.kind == StyleKind.MECHANICAL }
            .map { style -> style.id }
            .toSet()
        assertEquals(setOf("lowercase-casual", "normal-caps-casual"), mechanicalIds)
    }

    @Test
    fun `the default is normal-caps-casual and resolves through lookup`() {
        assertEquals("normal-caps-casual", StyleCatalog.DEFAULT.id)
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId("normal-caps-casual"))
    }

    @Test
    fun `an unknown id resolves to the default`() {
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId("not-a-style"))
        assertEquals(StyleCatalog.DEFAULT, StyleCatalog.byId(""))
    }
}
