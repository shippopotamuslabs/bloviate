package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [CustomStyleSyncPort] via the custom-styles callables (BLO-25 S4,
 * server BLO-26), mirroring [FirebaseStyleClient]: [AuthSessionPort]
 * signs the caller in first (a signed-out user gets [SignedOut] without
 * a network call), every callable or payload failure becomes a typed
 * [CustomStyleSyncFailure], and only cancellation is rethrown. Logs
 * carry outcome codes only — never style content.
 *
 * Payload parsing is defensive by design: the wire shape is `Map`s of
 * `Any?`, so a missing key or a wrong type maps onto
 * [CustomStyleSyncFailure.Unavailable], never a crash. One malformed
 * entry in a list drops that entry while its siblings survive (the S2
 * store's precedent for a corrupt persisted record); a misshapen
 * envelope fails the whole call.
 */
class FirebaseCustomStyleSyncClient @Inject constructor(
    private val gateway: CustomStyleSyncGateway,
    private val auth: AuthSessionPort,
) : CustomStyleSyncPort {

    override suspend fun list(): CustomStyleSyncOutcome<List<CustomStyleRecord>> {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "custom-styles outcome=signed-out op=list")
            return CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut)
        }
        return try {
            val styles = parseStyleList(gateway.callListCustomStyles())
                ?: return failed("list", CustomStyleSyncFailure.Unavailable)
            Log.d(TAG, "custom-styles outcome=ok op=list")
            CustomStyleSyncOutcome.Ok(styles)
        } catch (exception: StyleFunctionException) {
            failed("list", exception.code.toSyncFailure())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "custom-styles outcome=unavailable op=list", exception)
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)
        }
    }

    override suspend fun save(
        definition: StyleDefinition,
        expectedRevision: Long?,
    ): CustomStyleSyncOutcome<CustomStyleRecord> {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "custom-styles outcome=signed-out op=save")
            return CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut)
        }
        return try {
            val payload = gateway.callSaveCustomStyle(
                definition = definition.toRequestMap(includeId = expectedRevision != null),
                expectedRevision = expectedRevision,
            )
            val style = (payload as? Map<*, *>)?.get("style")?.toRecord()
                ?: return failed("save", CustomStyleSyncFailure.Unavailable)
            Log.d(TAG, "custom-styles outcome=ok op=save")
            CustomStyleSyncOutcome.Ok(style)
        } catch (exception: StyleFunctionException) {
            failed("save", exception.code.toSyncFailure())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "custom-styles outcome=unavailable op=save", exception)
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)
        }
    }

    override suspend fun delete(styleId: String): CustomStyleSyncOutcome<Boolean> {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "custom-styles outcome=signed-out op=delete")
            return CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut)
        }
        return try {
            val deleted = (gateway.callDeleteCustomStyle(styleId) as? Map<*, *>)?.get("deleted") as? Boolean
                ?: return failed("delete", CustomStyleSyncFailure.Unavailable)
            Log.d(TAG, "custom-styles outcome=ok op=delete")
            CustomStyleSyncOutcome.Ok(deleted)
        } catch (exception: StyleFunctionException) {
            failed("delete", exception.code.toSyncFailure())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "custom-styles outcome=unavailable op=delete", exception)
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable)
        }
    }

    private fun failed(
        op: String,
        failure: CustomStyleSyncFailure,
    ): CustomStyleSyncOutcome.Failed {
        Log.d(TAG, "custom-styles outcome=$failure op=$op")
        return CustomStyleSyncOutcome.Failed(failure)
    }

    private companion object {
        const val TAG: String = "CustomStyleSync"
    }
}

/** The canonical server style object (BLO-26 S3) decoded into a record, or null when malformed. */
private fun Any?.toRecord(): CustomStyleRecord? {
    val map = this as? Map<*, *> ?: return null
    return map.parseStyle()
}

private fun Map<*, *>.parseStyle(): CustomStyleRecord? {
    val id = string("id")?.takeIf { it.isNotBlank() }
    val kind: StyleKind? = enumValue("kind")
    val category: StyleCategory? = enumValue("category")
    if (id == null || kind == null || category == null) return null
    val fewShots = parseShots(this["fewShots"])
    val displayName = string("displayName")
    val description = string("description")
    if (fewShots == null || displayName == null || description == null) return null
    val sample = string("sample")
    val systemInstructions = string("systemInstructions")
    val revision = (this["revision"] as? Number)?.toLong()
    if (sample == null || systemInstructions == null || revision == null) return null
    val updatedAt = (this["updatedAtEpochMillis"] as? Number)?.toLong() ?: return null
    return CustomStyleRecord(
        definition = StyleDefinition(
            id = id,
            displayName = displayName,
            description = description,
            sample = sample,
            systemInstructions = systemInstructions,
            fewShots = fewShots,
            kind = kind,
            category = category,
        ),
        revision = revision,
        updatedAtEpochMillis = updatedAt,
    )
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String

/**
 * The wire carries lowercase enum names ("llm"/"custom"); the enums are
 * uppercase — match case-insensitively rather than crashing or dropping
 * on a future server-side casing change.
 */
private inline fun <reified E : Enum<E>> Map<*, *>.enumValue(key: String): E? {
    val raw = this[key] as? String ?: return null
    return enumValues<E>().firstOrNull { entry -> entry.name.equals(raw, ignoreCase = true) }
}

/** The few-shot pairs, skipping malformed pairs; null when the list itself is misshapen. */
private fun parseShots(value: Any?): List<FewShot>? {
    val items = value as? List<*> ?: return null
    val shots = mutableListOf<FewShot>()
    for (entry in items) {
        val pair = entry as? Map<*, *>
        val raw = pair?.get("raw") as? String
        val styled = pair?.get("styled") as? String
        if (raw != null && styled != null) shots += FewShot(raw = raw, styled = styled)
    }
    return shots
}

/** The list envelope decoded, dropping malformed entries; null when the envelope itself is misshapen. */
private fun parseStyleList(payload: Any?): List<CustomStyleRecord>? {
    val styles = (payload as? Map<*, *>)?.get("styles") as? List<*> ?: return null
    return styles.mapNotNull { entry -> entry.toRecord() }
}

/** The client-sent definition shape (BLO-26 S3): the six style fields plus the id on update only. */
private fun StyleDefinition.toRequestMap(includeId: Boolean): Map<String, Any?> =
    buildMap {
        if (includeId) put("id", id)
        put("displayName", displayName)
        put("description", description)
        put("sample", sample)
        put("systemInstructions", systemInstructions)
        put("fewShots", fewShots.map { shot -> mapOf("raw" to shot.raw, "styled" to shot.styled) })
    }

/** Callable error codes onto sync failures; anything unrecognized is a transport-grade problem. */
private fun String.toSyncFailure(): CustomStyleSyncFailure =
    when (this) {
        "unauthenticated" -> CustomStyleSyncFailure.SignedOut
        "permission-denied" -> CustomStyleSyncFailure.NotEntitled
        "invalid-argument" -> CustomStyleSyncFailure.Invalid
        "failed-precondition" -> CustomStyleSyncFailure.Conflict
        "resource-exhausted" -> CustomStyleSyncFailure.LimitReached
        else -> CustomStyleSyncFailure.Unavailable
    }
