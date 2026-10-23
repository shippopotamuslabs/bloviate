package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.application.usecase.DeleteSyncedCustomStyleUseCase
import com.shippopotamus.bloviate.application.usecase.DeleteSyncedCustomStyleOutcome
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Style tab ViewModel (ENC-72 + ENC-73, customs ENC-199). Depends on use
 * cases only; combines the merged catalog (customs first, then the
 * static built-ins) with the live selection into the presentation-only
 * [StyleUiState]. Every catalog style is selectable; the CUSTOM section
 * is always present so the create entry survives zero customs. Custom
 * deletes go through the synced delete use case behind a confirmation —
 * the dialog state and any rejection live here as transient presentation
 * state, like the Dictionary tab's add-form drafts.
 */
@HiltViewModel
class StyleViewModel @Inject constructor(
    observeCatalog: ObserveStyleCatalogUseCase,
    observeSelectedStyle: ObserveSelectedStyleUseCase,
    private val selectStyle: SelectStyleUseCase,
    private val deleteCustomStyle: DeleteSyncedCustomStyleUseCase,
    refreshCustomStyles: RefreshCustomStylesUseCase,
) : ViewModel() {

    init {
        // Custom-styles sync (BLO-25 S4): refresh the account cache when
        // the Style tab opens. Fire-and-forget: the outcome stays in the
        // use case, so a failed refresh never blocks the tab render — the
        // catalog keeps streaming the local cache either way.
        viewModelScope.launch {
            refreshCustomStyles()
        }
    }

    /** The delete awaiting confirmation, if any. */
    private val pendingDelete = MutableStateFlow<PendingStyleDelete?>(null)

    /** The last delete's rejection, until dismissed. */
    private val deleteError = MutableStateFlow<CustomStyleDeleteError?>(null)

    private val catalog: StateFlow<List<StyleDefinition>> = observeCatalog()
    private val selectedStyle: StateFlow<String> = observeSelectedStyle()

    val uiState: StateFlow<StyleUiState> = combine(
        catalog,
        selectedStyle,
        pendingDelete,
        deleteError,
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = buildUiState(
            catalog.value,
            selectedStyle.value,
            pendingDelete.value,
            deleteError.value,
        ),
    )

    /**
     * Groups the catalog by category in enum order, alphabetical by display
     * name within a section. The CUSTOM section is always present — with
     * zero customs it renders empty so the create entry stays visible.
     */
    private fun buildUiState(
        catalog: List<StyleDefinition>,
        selectedId: String,
        pendingDelete: PendingStyleDelete?,
        deleteError: CustomStyleDeleteError?,
    ): StyleUiState =
        StyleUiState(
            sections = StyleCategory.entries.map { category ->
                val styles = catalog.filter { style -> style.category == category }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { style -> style.displayName })
                    .map { style -> style.toOption(selectedId) }
                StyleSectionUi(headerRes = category.toHeaderRes(), styles = styles)
            },
            pendingDelete = pendingDelete,
            deleteError = deleteError,
        )

    private fun StyleCategory.toHeaderRes(): Int = when (this) {
        StyleCategory.CUSTOM -> R.string.style_section_custom
        StyleCategory.SERIOUS -> R.string.style_section_serious
        StyleCategory.PHILOSOPHERS -> R.string.style_section_philosophers
        StyleCategory.WRITERS -> R.string.style_section_writers
        StyleCategory.ERAS_AND_GENRES -> R.string.style_section_eras_and_genres
        StyleCategory.PERSONAS -> R.string.style_section_personas
        StyleCategory.MODERN_PARODY -> R.string.style_section_modern_parody
    }

    /** Persists the tapped style. */
    fun onStyleSelected(styleId: String) {
        viewModelScope.launch { selectStyle(styleId) }
    }

    /** Opens the delete confirmation for the tapped custom style. */
    fun onDeleteCustomClick(styleId: String) {
        val name = catalog.value.firstOrNull { style -> style.id == styleId }?.displayName ?: styleId
        pendingDelete.update { PendingStyleDelete(styleId = styleId, displayName = name) }
    }

    /** Closes the delete confirmation without deleting. */
    fun onDeleteDismiss() {
        pendingDelete.update { null }
    }

    /** Deletes the confirmed style through the server, then the cache; a failure surfaces inline. */
    fun onDeleteConfirm() {
        val styleId = pendingDelete.value?.styleId ?: return
        viewModelScope.launch {
            when (val outcome = deleteCustomStyle(styleId)) {
                DeleteSyncedCustomStyleOutcome.Deleted ->
                    pendingDelete.update { null }
                is DeleteSyncedCustomStyleOutcome.Failed -> {
                    pendingDelete.update { null }
                    deleteError.update { outcome.failure.toDeleteError() }
                }
            }
        }
    }

    /** Dismisses the delete error line. */
    fun onDeleteErrorDismiss() {
        deleteError.update { null }
    }

    private fun StyleDefinition.toOption(selectedId: String): StyleOptionUi =
        StyleOptionUi(
            id = id,
            displayName = displayName,
            description = description,
            sample = sample,
            requiresAi = kind == StyleKind.LLM,
            isSelected = id == selectedId,
            isCustom = category == StyleCategory.CUSTOM,
        )

    private fun CustomStyleSyncFailure.toDeleteError(): CustomStyleDeleteError =
        when (this) {
            CustomStyleSyncFailure.SignedOut -> CustomStyleDeleteError.SIGNED_OUT
            CustomStyleSyncFailure.NotEntitled -> CustomStyleDeleteError.NOT_ENTITLED
            CustomStyleSyncFailure.Invalid -> CustomStyleDeleteError.INVALID
            CustomStyleSyncFailure.Conflict -> CustomStyleDeleteError.CONFLICT
            CustomStyleSyncFailure.LimitReached -> CustomStyleDeleteError.LIMIT_REACHED
            CustomStyleSyncFailure.Unavailable -> CustomStyleDeleteError.UNAVAILABLE
        }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
