package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.application.port.TextInsertionPort

/**
 * Hand-written fake of [TextInsertionPort]; collects every text it was
 * asked to insert and reports [nextResult] back.
 */
class FakeTextInsertionPort : TextInsertionPort {

    /** Result reported for every insert. Defaults to a successful paste. */
    var nextResult: InsertionResult =
        InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE)

    private val mutableInsertedTexts = mutableListOf<String>()

    /** Texts passed to [insert] so far, in order. */
    val insertedTexts: List<String> get() = mutableInsertedTexts.toList()

    override suspend fun insert(text: String): InsertionResult {
        mutableInsertedTexts += text
        return nextResult
    }
}
