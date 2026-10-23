package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.R
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Style tab ViewModel (ENC-72 + ENC-73). Depends on use cases only; maps
 * the static catalog plus the live selection into the presentation-only
 * [StyleUiState]. Every catalog style is selectable.
 */
@HiltViewModel
class StyleViewModel @Inject constructor(
    observeCatalog: ObserveStyleCatalogUseCase,
    observeSelectedStyle: ObserveSelectedStyleUseCase,
    private val selectStyle: SelectStyleUseCase,
) : ViewModel() {

    private val catalog: List<StyleDefinition> = observeCatalog()

    val uiState: StateFlow<StyleUiState> = observeSelectedStyle()
        .map { selectedId -> buildUiState(selectedId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
            initialValue = buildUiState(observeSelectedStyle().value),
        )

    /** Groups the catalog by category in enum order, alphabetical by display name within a section. */
    private fun buildUiState(selectedId: String): StyleUiState =
        StyleUiState(
            sections = StyleCategory.entries.mapNotNull { category ->
                val styles = catalog.filter { style -> style.category == category }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { style -> style.displayName })
                    .map { style -> style.toOption(selectedId) }
                if (styles.isEmpty()) {
                    null
                } else {
                    StyleSectionUi(headerRes = category.toHeaderRes(), styles = styles)
                }
            },
        )

    private fun StyleCategory.toHeaderRes(): Int = when (this) {
        StyleCategory.SERIOUS -> R.string.style_section_serious
        StyleCategory.ANCIENT_PHILOSOPHERS -> R.string.style_section_ancient_philosophers
        StyleCategory.WRITERS -> R.string.style_section_writers
        StyleCategory.ERAS_AND_GENRES -> R.string.style_section_eras_and_genres
        StyleCategory.PERSONAS -> R.string.style_section_personas
        StyleCategory.MODERN_PARODY -> R.string.style_section_modern_parody
    }

    /** Persists the tapped style. */
    fun onStyleSelected(styleId: String) {
        viewModelScope.launch { selectStyle(styleId) }
    }

    private fun StyleDefinition.toOption(selectedId: String): StyleOptionUi =
        StyleOptionUi(
            id = id,
            displayName = displayName,
            description = description,
            sample = sample,
            requiresAi = kind == StyleKind.LLM,
            isSelected = id == selectedId,
        )

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
