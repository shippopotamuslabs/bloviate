package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.GeneratedStyleDraft
import com.shippopotamus.bloviate.application.usecase.GenerateCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.SaveSyncedCustomStyleOutcome
import com.shippopotamus.bloviate.application.usecase.SaveSyncedCustomStyleUseCase
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Character caps mirrored from [CustomStyleValidator] for hints and
 * per-pair error attribution only — validation itself always runs through
 * the validator, so these can never loosen what saves.
 */
internal const val MAX_CUSTOM_STYLE_NAME_CHARS: Int = 40
internal const val MAX_CUSTOM_STYLE_DESCRIPTION_CHARS: Int = 120
internal const val MAX_CUSTOM_STYLE_SAMPLE_CHARS: Int = 180
internal const val MAX_CUSTOM_STYLE_INSTRUCTIONS_CHARS: Int = 2400
internal const val MAX_CUSTOM_STYLE_FEWSHOT_SIDE_CHARS: Int = 1500

/**
 * Manual custom-style editor ViewModel (BLO-25 S5). Depends on use cases
 * only: the source record streams from [ObserveCustomStylesUseCase], saves
 * go through [SaveSyncedCustomStyleUseCase], and every keystroke
 * re-validates through the domain [CustomStyleValidator]. The draft is
 * plain strings — no domain types — and the save button stays disabled
 * until the draft validates.
 *
 * A create carries no id (the server allocates it): validation runs
 * against a temporary placeholder id that is never sent — the write path
 * sends a blank id with a null revision, and the use case keeps that
 * contract. An edit sends the real id with the revision read at load, so
 * a concurrent change elsewhere surfaces as a conflict instead of a
 * silent overwrite.
 *
 * Generated mode (BLO-160 S7): [loadGenerated] opens a create prefilled
 * from an AI draft, keeping the draft as the clean baseline and the
 * source description for [onRegenerateClick]. Edit-existing targets never
 * enter generated mode, so they never regenerate.
 */
@HiltViewModel
class CustomStyleEditorViewModel @Inject constructor(
    private val observeCustoms: ObserveCustomStylesUseCase,
    private val saveSynced: SaveSyncedCustomStyleUseCase,
    private val generate: GenerateCustomStyleUseCase,
    private val validator: CustomStyleValidator,
) : ViewModel() {

    /** The loaded save target: null ids are a create, which has no revision and no original. */
    private data class LoadedTarget(
        val styleId: String?,
        val revision: Long?,
        val original: StyleDefinition?,
    )

    /** The string draft; pairs are raw/styled sides in order. */
    private data class Draft(
        val displayName: String = "",
        val description: String = "",
        val sample: String = "",
        val systemInstructions: String = "",
        val pairs: List<Pair<String, String>> = listOf("" to "", "" to ""),
    ) {
        companion object {
            fun from(definition: StyleDefinition): Draft = Draft(
                displayName = definition.displayName,
                description = definition.description,
                sample = definition.sample,
                systemInstructions = definition.systemInstructions,
                pairs = definition.fewShots.map { shot -> shot.raw to shot.styled },
            )

            fun fromGenerated(generated: GeneratedStyleDraft): Draft = Draft(
                displayName = generated.displayName,
                description = generated.description,
                sample = generated.sample,
                systemInstructions = generated.systemInstructions,
                pairs = generated.fewShots.map { shot -> shot.raw to shot.styled },
            )
        }
    }

    /**
     * The generated clean baseline plus the source description that
     * produced it (BLO-160 S7). Null outside generated mode; set by
     * [loadGenerated] and replaced by every successful regenerate, so the
     * draft reads clean again right after a fresh draft lands.
     */
    private data class GeneratedBasis(
        val draft: Draft,
        val sourceDescription: String,
    )

    private val loaded = MutableStateFlow<LoadedTarget?>(null)
    private val draft = MutableStateFlow(Draft())
    private val saving = MutableStateFlow(false)
    private val saveError = MutableStateFlow<CustomStyleSaveError?>(null)
    private val loadFailed = MutableStateFlow(false)
    private val generatedBasis = MutableStateFlow<GeneratedBasis?>(null)
    private val regenerating = MutableStateFlow(false)
    private val regenerateError = MutableStateFlow<CustomStyleGenerationError?>(null)
    private val regenerateConfirmPending = MutableStateFlow(false)

    private var hasLoadedFor: String? = null
    private var hasLoaded: Boolean = false
    private var savedSinceLoad: Boolean = false
    private var loadGeneration: Int = 0

    /**
     * The two [uiState] slices, combined separately: this coroutines build
     * ships `combine` overloads only up to five flows, so the nine
     * underlying flows ride as two groups.
     */
    private data class EditorBase(
        val target: LoadedTarget?,
        val draft: Draft,
        val saving: Boolean,
        val saveError: CustomStyleSaveError?,
        val loadFailed: Boolean,
    )

    /** The generated-mode slice of [CustomStyleEditorUiState]. */
    private data class GeneratedUi(
        val basis: GeneratedBasis?,
        val regenerating: Boolean,
        val error: CustomStyleGenerationError?,
        val confirmPending: Boolean,
    )

    private val editorBase: Flow<EditorBase> = combine(
        loaded,
        draft,
        saving,
        saveError,
        loadFailed,
        ::EditorBase,
    )

    private val generatedUi: Flow<GeneratedUi> = combine(
        generatedBasis,
        regenerating,
        regenerateError,
        regenerateConfirmPending,
        ::GeneratedUi,
    )

    val uiState: StateFlow<CustomStyleEditorUiState> = combine(
        editorBase,
        generatedUi,
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = buildUiState(
            EditorBase(
                target = loaded.value,
                draft = draft.value,
                saving = saving.value,
                saveError = saveError.value,
                loadFailed = loadFailed.value,
            ),
            GeneratedUi(
                basis = generatedBasis.value,
                regenerating = regenerating.value,
                error = regenerateError.value,
                confirmPending = regenerateConfirmPending.value,
            ),
        ),
    )

    /** One-shot: the save landed, so the route can navigate back to the list. */
    private val saveSucceededChannel = Channel<Unit>(Channel.BUFFERED)
    val saveSucceeded: Flow<Unit> = saveSucceededChannel.receiveAsFlow()

    /**
     * Starts (or restarts) the editor for [styleId]: null is a blank
     * create, anything else resolves against the loaded customs cache. A
     * repeat call for the same target is a no-op so rotation and
     * recomposition never clobber the draft; a new target resets it.
     */
    fun load(styleId: String?) {
        // A lingering generated basis blocks the same-target no-op: the
        // route reaches load() without a handoff only when the user asked
        // for a manual target, so a stale AI draft must reset, never
        // survive behind the early return.
        val isSameTargetReload = hasLoaded && hasLoadedFor == styleId && !savedSinceLoad
        if (isSameTargetReload && generatedBasis.value == null) {
            return
        }
        hasLoaded = true
        hasLoadedFor = styleId
        savedSinceLoad = false
        loadGeneration++
        saving.update { false }
        saveError.update { null }
        loadFailed.update { false }
        generatedBasis.update { null }
        regenerating.update { false }
        regenerateError.update { null }
        regenerateConfirmPending.update { false }
        if (styleId == null) {
            loaded.update { LoadedTarget(styleId = null, revision = null, original = null) }
            draft.update { Draft() }
            return
        }
        loaded.update { null }
        draft.update { Draft() }
        val generation = loadGeneration
        viewModelScope.launch {
            val record = observeCustoms().first { state -> state.isLoaded }
                .styles.firstOrNull { style -> style.definition.id == styleId }
            if (generation != loadGeneration) {
                return@launch
            }
            if (record == null) {
                loadFailed.update { true }
            } else {
                loaded.update {
                    LoadedTarget(
                        styleId = styleId,
                        revision = record.revision,
                        original = record.definition,
                    )
                }
                draft.update { Draft.from(record.definition) }
            }
        }
    }

    /**
     * Opens a create prefilled from an AI draft (BLO-160 S7): the draft
     * fields land verbatim for review, the draft becomes the clean
     * baseline, and [sourceDescription] is retained for regenerates. The
     * save path is unchanged — a generated create still sends a blank id
     * with a null revision, and the server allocates the id.
     */
    fun loadGenerated(draft: GeneratedStyleDraft, sourceDescription: String) {
        hasLoaded = true
        hasLoadedFor = null
        savedSinceLoad = false
        loadGeneration++
        saving.update { false }
        saveError.update { null }
        loadFailed.update { false }
        regenerating.update { false }
        regenerateError.update { null }
        regenerateConfirmPending.update { false }
        val fresh = Draft.fromGenerated(draft)
        generatedBasis.update { GeneratedBasis(draft = fresh, sourceDescription = sourceDescription) }
        loaded.update { LoadedTarget(styleId = null, revision = null, original = null) }
        this.draft.update { fresh }
    }

    fun onDisplayNameChange(value: String) {
        updateDraft { copy(displayName = value) }
    }

    fun onDescriptionChange(value: String) {
        updateDraft { copy(description = value) }
    }

    fun onSampleChange(value: String) {
        updateDraft { copy(sample = value) }
    }

    fun onInstructionsChange(value: String) {
        updateDraft { copy(systemInstructions = value) }
    }

    fun onPairRawChange(index: Int, value: String) {
        updateDraft {
            copy(pairs = pairs.mapIndexed { i, pair -> if (i == index) value to pair.second else pair })
        }
    }

    fun onPairStyledChange(index: Int, value: String) {
        updateDraft {
            copy(pairs = pairs.mapIndexed { i, pair -> if (i == index) pair.first to value else pair })
        }
    }

    /** Adds an empty pair, up to [MAX_FEWSHOT_PAIRS]. */
    fun onAddPair() {
        updateDraft {
            if (pairs.size >= MAX_FEWSHOT_PAIRS) {
                this
            } else {
                copy(pairs = pairs + ("" to ""))
            }
        }
    }

    /** Removes the pair at [index], down to [MIN_FEWSHOT_PAIRS]. */
    fun onRemovePair(index: Int) {
        updateDraft {
            if (pairs.size <= MIN_FEWSHOT_PAIRS || index !in pairs.indices) {
                this
            } else {
                copy(pairs = pairs.filterIndexed { i, _ -> i != index })
            }
        }
    }

    /**
     * Dismisses the error lines; the draft is kept. Save and regenerate
     * failures share one dismiss because they share one screen and one
     * retry posture — a failed call reports inline, and the next keystroke
     * clears it anyway.
     */
    fun onSaveErrorDismiss() {
        saveError.update { null }
        regenerateError.update { null }
    }

    /**
     * Regenerate entry point (BLO-160 S7, generated mode only): a clean
     * draft regenerates immediately, a dirty one raises the route's
     * discard-confirmation first. A no-op outside generated mode, so
     * edit-existing can never regenerate.
     */
    fun onRegenerateClick() {
        val basis = generatedBasis.value ?: return
        if (draft.value != basis.draft) {
            regenerateConfirmPending.update { true }
        } else {
            launchRegenerate(basis.sourceDescription)
        }
    }

    /** Confirms the dirty-draft discard and regenerates from the retained description. */
    fun onRegenerateConfirm() {
        regenerateConfirmPending.update { false }
        val basis = generatedBasis.value ?: return
        launchRegenerate(basis.sourceDescription)
    }

    /** Keeps editing; the dirty draft is untouched. */
    fun onRegenerateDismissConfirm() {
        regenerateConfirmPending.update { false }
    }

    /**
     * Regenerates from [sourceDescription] and replaces the draft fields,
     * resetting the clean baseline — the fresh draft reads clean, and the
     * retained description is unchanged, so back-to-back regenerates are
     * always possible. A regenerate failure keeps the draft and reports.
     */
    private fun launchRegenerate(sourceDescription: String) {
        if (saving.value || regenerating.value) {
            return
        }
        viewModelScope.launch {
            regenerating.update { true }
            regenerateError.update { null }
            when (val outcome = generate(sourceDescription)) {
                is CustomStyleGenerationOutcome.Ok -> {
                    val fresh = Draft.fromGenerated(outcome.draft)
                    draft.update { fresh }
                    generatedBasis.update {
                        GeneratedBasis(draft = fresh, sourceDescription = sourceDescription)
                    }
                }
                is CustomStyleGenerationOutcome.Failed ->
                    regenerateError.update { outcome.failure.toGenerationError() }
            }
            regenerating.update { false }
        }
    }

    /**
     * Saves the draft through the server. Disabled while invalid or
     * already saving; a conflict keeps the draft and reports, so the user
     * can review and retry.
     */
    fun onSaveClick() {
        val target = loaded.value ?: return
        val snapshot = draft.value
        val violations = validator.validate(
            snapshot.toDefinition(target.styleId).forValidation(target.styleId),
        )
        if (saving.value || regenerating.value || violations.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            saving.update { true }
            val outcome = saveSynced(snapshot.toDefinition(target.styleId), target.revision)
            saving.update { false }
            when (outcome) {
                is SaveSyncedCustomStyleOutcome.Saved -> {
                    savedSinceLoad = true
                    saveError.update { null }
                    saveSucceededChannel.send(Unit)
                }
                is SaveSyncedCustomStyleOutcome.Invalid ->
                    saveError.update { CustomStyleSaveError.INVALID }
                is SaveSyncedCustomStyleOutcome.Failed ->
                    saveError.update { outcome.failure.toSaveError() }
            }
        }
    }

    /**
     * A create is dirty once any field differs from the blank start; an
     * edit is dirty once the draft differs from the loaded record; a
     * generated create is dirty once the draft differs from the last
     * generated baseline. While the target is still resolving, nothing
     * counts as dirty.
     */
    private fun LoadedTarget?.isDirty(draft: Draft, basis: GeneratedBasis?): Boolean =
        when {
            this == null -> false
            basis != null -> draft != basis.draft
            original == null -> draft != Draft()
            else -> draft != Draft.from(original)
        }

    private fun updateDraft(transform: Draft.() -> Draft) {
        draft.update(transform)
        saveError.update { null }
        regenerateError.update { null }
    }

    private fun buildUiState(
        base: EditorBase,
        generated: GeneratedUi,
    ): CustomStyleEditorUiState {
        val target = base.target
        if (target == null && !base.loadFailed) {
            return CustomStyleEditorUiState(isSaving = base.saving)
        }
        val draft = base.draft
        val violations = validator.validate(draft.toDefinition(target?.styleId).forValidation(target?.styleId))
        return CustomStyleEditorUiState(
            isCreate = target?.styleId == null,
            displayName = draft.displayName,
            description = draft.description,
            sample = draft.sample,
            systemInstructions = draft.systemInstructions,
            pairs = draft.pairs.map { pair -> pair.toPairUi(violations) },
            fieldErrors = violations.toFieldErrors(),
            isValid = violations.isEmpty(),
            canAddPair = draft.pairs.size < MAX_FEWSHOT_PAIRS,
            canRemovePair = draft.pairs.size > MIN_FEWSHOT_PAIRS,
            isSaving = base.saving,
            saveError = base.saveError,
            isDirty = target.isDirty(draft, generated.basis),
            loadFailed = base.loadFailed,
            isGenerated = generated.basis != null,
            isRegenerating = generated.regenerating,
            regenerateError = generated.error,
            regenerateConfirmPending = generated.confirmPending,
        )
    }

    private fun Draft.toDefinition(styleId: String?): StyleDefinition = StyleDefinition(
        id = styleId.orEmpty(),
        displayName = displayName,
        description = description,
        sample = sample,
        systemInstructions = systemInstructions,
        fewShots = pairs.map { pair -> FewShot(raw = pair.first, styled = pair.second) },
        kind = StyleKind.LLM,
        category = StyleCategory.CUSTOM,
    )

    /**
     * A create carries no id yet but the validator requires the `custom-`
     * prefix, so validation alone runs against a temporary placeholder —
     * the write path ([onSaveClick]) always sends the blank id, never this.
     */
    private fun StyleDefinition.forValidation(styleId: String?): StyleDefinition =
        if (styleId == null) copy(id = CREATE_ID_PLACEHOLDER) else this

    private fun List<CustomStyleViolation>.toFieldErrors(): Map<CustomStyleEditorField, Int> =
        buildMap {
            if (CustomStyleViolation.DISPLAY_NAME_BLANK in this@toFieldErrors) {
                put(CustomStyleEditorField.DISPLAY_NAME, R.string.style_editor_error_name_blank)
            }
            if (CustomStyleViolation.DISPLAY_NAME_TOO_LONG in this@toFieldErrors) {
                put(CustomStyleEditorField.DISPLAY_NAME, R.string.style_editor_error_name_too_long)
            }
            if (CustomStyleViolation.DESCRIPTION_BLANK in this@toFieldErrors) {
                put(CustomStyleEditorField.DESCRIPTION, R.string.style_editor_error_description_blank)
            }
            if (CustomStyleViolation.DESCRIPTION_TOO_LONG in this@toFieldErrors) {
                put(CustomStyleEditorField.DESCRIPTION, R.string.style_editor_error_description_too_long)
            }
            if (CustomStyleViolation.SAMPLE_BLANK in this@toFieldErrors) {
                put(CustomStyleEditorField.SAMPLE, R.string.style_editor_error_sample_blank)
            }
            if (CustomStyleViolation.SAMPLE_TOO_LONG in this@toFieldErrors) {
                put(CustomStyleEditorField.SAMPLE, R.string.style_editor_error_sample_too_long)
            }
            if (CustomStyleViolation.INSTRUCTIONS_BLANK in this@toFieldErrors) {
                put(CustomStyleEditorField.INSTRUCTIONS, R.string.style_editor_error_instructions_blank)
            }
            if (CustomStyleViolation.INSTRUCTIONS_TOO_LONG in this@toFieldErrors) {
                put(CustomStyleEditorField.INSTRUCTIONS, R.string.style_editor_error_instructions_too_long)
            }
        }

    /**
     * Attributes the flat few-shot violations to their pair: a blank side
     * always belongs to its own pair, and a too-long side to the pair that
     * actually exceeds the cap.
     */
    private fun Pair<String, String>.toPairUi(violations: List<CustomStyleViolation>): FewShotPairUi =
        FewShotPairUi(
            raw = first,
            styled = second,
            rawErrorRes = when {
                first.isBlank() && CustomStyleViolation.FEWSHOT_RAW_BLANK in violations ->
                    R.string.style_editor_error_pair_raw_blank
                CustomStyleViolation.FEWSHOT_RAW_TOO_LONG in violations &&
                    first.length > MAX_CUSTOM_STYLE_FEWSHOT_SIDE_CHARS ->
                    R.string.style_editor_error_pair_raw_too_long
                else -> null
            },
            styledErrorRes = when {
                second.isBlank() && CustomStyleViolation.FEWSHOT_STYLED_BLANK in violations ->
                    R.string.style_editor_error_pair_styled_blank
                CustomStyleViolation.FEWSHOT_STYLED_TOO_LONG in violations &&
                    second.length > MAX_CUSTOM_STYLE_FEWSHOT_SIDE_CHARS ->
                    R.string.style_editor_error_pair_styled_too_long
                else -> null
            },
        )

    private fun CustomStyleSyncFailure.toSaveError(): CustomStyleSaveError =
        when (this) {
            CustomStyleSyncFailure.SignedOut -> CustomStyleSaveError.SIGNED_OUT
            CustomStyleSyncFailure.NotEntitled -> CustomStyleSaveError.NOT_ENTITLED
            CustomStyleSyncFailure.Invalid -> CustomStyleSaveError.INVALID
            CustomStyleSyncFailure.Conflict -> CustomStyleSaveError.CONFLICT
            CustomStyleSyncFailure.LimitReached -> CustomStyleSaveError.LIMIT_REACHED
            CustomStyleSyncFailure.Unavailable -> CustomStyleSaveError.UNAVAILABLE
        }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS: Long = 5_000L
        const val MIN_FEWSHOT_PAIRS: Int = 2
        const val MAX_FEWSHOT_PAIRS: Int = 5

        /** Temporary validation-only id for creates; never sent to the server (see [forValidation]). */
        const val CREATE_ID_PLACEHOLDER: String = "custom-draft"
    }
}
