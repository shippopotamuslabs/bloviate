package com.shippopotamus.bloviate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.application.usecase.AddDictionaryCorrectionUseCase
import com.shippopotamus.bloviate.application.usecase.AddDictionaryWordUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveDictionaryUseCase
import com.shippopotamus.bloviate.application.usecase.RemoveDictionaryCorrectionUseCase
import com.shippopotamus.bloviate.application.usecase.RemoveDictionaryWordUseCase
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
 * Dictionary tab ViewModel (ENC-74). Depends on use cases only; maps the
 * domain dictionary into the presentation-only [DictionaryUiState]. The
 * add-form drafts and their rejection messages are transient
 * presentation state held here — the durable facts (words, corrections)
 * always come from [ObserveDictionaryUseCase].
 */
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    observeDictionary: ObserveDictionaryUseCase,
    private val addWord: AddDictionaryWordUseCase,
    private val removeWord: RemoveDictionaryWordUseCase,
    private val addCorrection: AddDictionaryCorrectionUseCase,
    private val removeCorrection: RemoveDictionaryCorrectionUseCase,
) : ViewModel() {

    /** The add forms' drafts + rejection messages; everything else in the UI state is observed. */
    private data class FormState(
        val wordDraft: String = "",
        val heardAsDraft: String = "",
        val replacementDraft: String = "",
        val wordError: DictionaryEditError? = null,
        val correctionError: DictionaryEditError? = null,
    )

    private val formState = MutableStateFlow(FormState())

    val uiState: StateFlow<DictionaryUiState> = combine(
        observeDictionary(),
        formState,
    ) { dictionary, form ->
        DictionaryUiState(
            words = dictionary.words,
            corrections = dictionary.corrections.map { correction ->
                CorrectionUi(heardAs = correction.heardAs, replacement = correction.replacement)
            },
            wordDraft = form.wordDraft,
            heardAsDraft = form.heardAsDraft,
            replacementDraft = form.replacementDraft,
            wordError = form.wordError,
            correctionError = form.correctionError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT_MS),
        initialValue = DictionaryUiState(
            words = observeDictionary().value.words,
            corrections = observeDictionary().value.corrections.map { correction ->
                CorrectionUi(heardAs = correction.heardAs, replacement = correction.replacement)
            },
        ),
    )

    fun onWordDraftChange(value: String) {
        formState.update { form -> form.copy(wordDraft = value, wordError = null) }
    }

    fun onHeardAsDraftChange(value: String) {
        formState.update { form -> form.copy(heardAsDraft = value, correctionError = null) }
    }

    fun onReplacementDraftChange(value: String) {
        formState.update { form -> form.copy(replacementDraft = value, correctionError = null) }
    }

    /** Adds the word draft; on success the draft clears, on rejection the reason shows under the form. */
    fun onAddWordClick() {
        val draft = formState.value.wordDraft
        viewModelScope.launch {
            when (addWord(draft)) {
                DictionaryEditResult.APPLIED ->
                    formState.update { form -> form.copy(wordDraft = "", wordError = null) }
                DictionaryEditResult.BLANK ->
                    formState.update { form -> form.copy(wordError = DictionaryEditError.BLANK_WORD) }
                DictionaryEditResult.DUPLICATE ->
                    formState.update { form -> form.copy(wordError = DictionaryEditError.DUPLICATE_WORD) }
                DictionaryEditResult.SELF_CORRECTION,
                DictionaryEditResult.NOT_FOUND,
                -> Unit // Not reachable outcomes for an add; nothing to surface.
            }
        }
    }

    /** Removes the word on the tapped row. */
    fun onRemoveWordClick(word: String) {
        viewModelScope.launch { removeWord(word) }
    }

    /** Adds the correction drafts as a pair; on success both drafts clear, on rejection the reason shows. */
    fun onAddCorrectionClick() {
        val form = formState.value
        viewModelScope.launch {
            when (addCorrection(form.heardAsDraft, form.replacementDraft)) {
                DictionaryEditResult.APPLIED ->
                    formState.update { current ->
                        current.copy(heardAsDraft = "", replacementDraft = "", correctionError = null)
                    }
                DictionaryEditResult.BLANK ->
                    formState.update { current ->
                        current.copy(correctionError = DictionaryEditError.BLANK_CORRECTION)
                    }
                DictionaryEditResult.DUPLICATE ->
                    formState.update { current ->
                        current.copy(correctionError = DictionaryEditError.DUPLICATE_CORRECTION)
                    }
                DictionaryEditResult.SELF_CORRECTION ->
                    formState.update { current ->
                        current.copy(correctionError = DictionaryEditError.SELF_CORRECTION)
                    }
                DictionaryEditResult.NOT_FOUND -> Unit // Not a reachable outcome for an add.
            }
        }
    }

    /** Removes the correction on the tapped row. */
    fun onRemoveCorrectionClick(heardAs: String) {
        viewModelScope.launch { removeCorrection(heardAs) }
    }

    private companion object {
        const val STATE_SHARING_TIMEOUT_MS = 5_000L
    }
}
