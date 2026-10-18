package com.shippopotamus.bloviate.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainTabTest {

    @Test
    fun `the default tab is setup`() {
        assertEquals(MainTab.SETUP, MainTab.DEFAULT)
    }

    @Test
    fun `every tab round trips through its saved value`() {
        MainTab.entries.forEach { tab ->
            assertEquals(tab, MainTab.fromSavedValue(tab.toSavedValue()))
        }
    }

    @Test
    fun `a null saved value falls back to the default tab`() {
        assertEquals(MainTab.DEFAULT, MainTab.fromSavedValue(null))
    }

    @Test
    fun `a blank saved value falls back to the default tab`() {
        assertEquals(MainTab.DEFAULT, MainTab.fromSavedValue(""))
    }

    @Test
    fun `an unknown saved value falls back to the default tab`() {
        assertEquals(MainTab.DEFAULT, MainTab.fromSavedValue("NOT_A_TAB"))
    }

    @Test
    fun `saved values are matched exactly`() {
        assertEquals(MainTab.DEFAULT, MainTab.fromSavedValue("setup"))
    }
}
