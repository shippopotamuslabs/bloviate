package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        .map { selectedId ->
            StyleUiState(
                options = catalog.map { style -> style.toOption(selectedId) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
            initialValue = StyleUiState(
                options = catalog.map { style ->
                    style.toOption(observeSelectedStyle().value)
                },
            ),
        )

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
