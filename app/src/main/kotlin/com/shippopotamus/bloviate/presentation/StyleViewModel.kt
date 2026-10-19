package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.domain.usecase.ClearApiKeyUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveHasApiKeyUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveStyleCatalogUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveApiKeyUseCase
import com.shippopotamus.bloviate.domain.usecase.SelectStyleUseCase
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
 * Style tab ViewModel (ENC-72 + ENC-73). Depends on use cases only; maps
 * the static catalog plus the live selection and key presence into the
 * presentation-only [StyleUiState]. LLM rows are disabled while no key is
 * stored, so a keyless tap can never select a style the pipeline would
 * silently pass through. Save/Clear need no one-shot event — the key
 * presence flow re-renders the card and the rows by itself.
 */
@HiltViewModel
class StyleViewModel @Inject constructor(
    observeCatalog: ObserveStyleCatalogUseCase,
    observeSelectedStyle: ObserveSelectedStyleUseCase,
    private val selectStyle: SelectStyleUseCase,
    observeHasApiKey: ObserveHasApiKeyUseCase,
    private val saveApiKey: SaveApiKeyUseCase,
    private val clearApiKey: ClearApiKeyUseCase,
) : ViewModel() {

    private val catalog: List<StyleDefinition> = observeCatalog()

    private val hasApiKey: StateFlow<Boolean> = observeHasApiKey()

    private val apiKeyDraft = MutableStateFlow("")

    val uiState: StateFlow<StyleUiState> = combine(
        observeSelectedStyle(),
        hasApiKey,
        apiKeyDraft,
    ) { selectedId, keyStored, draft ->
        StyleUiState(
            options = catalog.map { style -> style.toOption(selectedId, keyStored) },
            apiKeyDraft = draft,
            hasApiKey = keyStored,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = StyleUiState(
            options = catalog.map { style ->
                style.toOption(observeSelectedStyle().value, hasApiKey.value)
            },
            hasApiKey = hasApiKey.value,
        ),
    )

    /** Persists the tapped style; disabled LLM rows never reach here. */
    fun onStyleSelected(styleId: String) {
        if (StyleCatalog.byId(styleId).kind == StyleKind.LLM && !hasApiKey.value) return
        viewModelScope.launch { selectStyle(styleId) }
    }

    /** The transient key field; never persisted until Save. */
    fun onApiKeyDraftChange(draft: String) {
        apiKeyDraft.update { draft }
    }

    /** Encrypts and stores the draft, then clears the field. */
    fun onSaveApiKeyClick() {
        viewModelScope.launch {
            saveApiKey(apiKeyDraft.value)
            apiKeyDraft.update { "" }
        }
    }

    /** Removes the stored key; LLM styles fall back to local text. */
    fun onClearApiKeyClick() {
        viewModelScope.launch { clearApiKey() }
    }

    private fun StyleDefinition.toOption(selectedId: String, keyStored: Boolean): StyleOptionUi {
        val requiresAi = kind == StyleKind.LLM
        return StyleOptionUi(
            id = id,
            displayName = displayName,
            description = description,
            sample = sample,
            requiresAi = requiresAi,
            isSelected = id == selectedId,
            isEnabled = !requiresAi || keyStored,
        )
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
