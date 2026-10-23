package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.FocusedNodeSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldFocusEvaluatorTest {

    @Test
    fun `editable EditText node classifies as EditableFocus`() {
        val snapshot = snapshot(isEditable = true, className = "android.widget.EditText")

        assertEquals(
            FieldFocusState.EditableFocus(PACKAGE_NAME),
            FieldFocusEvaluator.evaluate(snapshot),
        )
    }

    @Test
    fun `password node classifies as PasswordFocus even though it is editable`() {
        val snapshot = snapshot(
            isEditable = true,
            isPassword = true,
            className = "android.widget.EditText",
        )

        assertEquals(
            FieldFocusState.PasswordFocus(PACKAGE_NAME),
            FieldFocusEvaluator.evaluate(snapshot),
        )
    }

    @Test
    fun `non-editable focused node classifies as NoEditableFocus`() {
        val snapshot = snapshot(isEditable = false, className = "android.widget.TextView")

        assertEquals(
            FieldFocusState.NoEditableFocus,
            FieldFocusEvaluator.evaluate(snapshot),
        )
    }

    @Test
    fun `null snapshot classifies as NoEditableFocus`() {
        assertEquals(
            FieldFocusState.NoEditableFocus,
            FieldFocusEvaluator.evaluate(null),
        )
    }

    @Test
    fun `editable WebView node classifies as EditableFocus`() {
        val snapshot = snapshot(isEditable = true, className = "android.webkit.WebView")

        assertEquals(
            FieldFocusState.EditableFocus(PACKAGE_NAME),
            FieldFocusEvaluator.evaluate(snapshot),
        )
    }

    @Test
    fun `EditText class name classifies as editable when isEditable lies`() {
        val snapshot = snapshot(isEditable = false, className = "com.example.widget.CustomEditText")

        assertEquals(
            FieldFocusState.EditableFocus(PACKAGE_NAME),
            FieldFocusEvaluator.evaluate(snapshot),
        )
    }

    private fun snapshot(
        isEditable: Boolean,
        isPassword: Boolean = false,
        className: String?,
    ): FocusedNodeSnapshot = FocusedNodeSnapshot(
        packageName = PACKAGE_NAME,
        isEditable = isEditable,
        isPassword = isPassword,
        className = className,
    )

    private companion object {
        const val PACKAGE_NAME = "com.example.host"
    }
}
