package com.shippopotamus.bloviate.data

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldFocusStateHolderTest {

    private val holder = FieldFocusStateHolder()

    @Test
    fun `initial state is NoEditableFocus`() {
        assertEquals(FieldFocusState.NoEditableFocus, holder.fieldFocusState.value)
    }

    @Test
    fun `duplicate states are not re-emitted and transitions emit in order`() = runTest {
        holder.fieldFocusState.test {
            assertEquals(FieldFocusState.NoEditableFocus, awaitItem())

            holder.publish(FieldFocusState.EditableFocus(PACKAGE_NAME))
            assertEquals(FieldFocusState.EditableFocus(PACKAGE_NAME), awaitItem())

            holder.publish(FieldFocusState.EditableFocus(PACKAGE_NAME))
            expectNoEvents()

            holder.publish(FieldFocusState.PasswordFocus(PACKAGE_NAME))
            assertEquals(FieldFocusState.PasswordFocus(PACKAGE_NAME), awaitItem())

            holder.publish(FieldFocusState.NoEditableFocus)
            assertEquals(FieldFocusState.NoEditableFocus, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.example.host"
    }
}
