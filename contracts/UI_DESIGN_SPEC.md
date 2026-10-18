# UI_DESIGN_SPEC.md
**Bloviate – UI Design Contract v1.0**
(Adapted from Enclave `UI_DESIGN_SPEC.md` v2.6 — its theme-token, preview, and rigor rules; Enclave's product-specific theme families and chat shapes do not apply here.)

UI contract for Bloviate's two surfaces: the in-app screens (onboarding, settings, model manager) and the floating bubble. Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation patterns and the overlay service rules).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use the app theme + Material 3 semantic color roles everywhere. | Hard-code hex colors outside the theme definition (except the specific semantic error color for the bubble's Error state). |
| Include `@Preview` for shared components covering **Light + Dark**. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`, `() -> Float` for RMS/progress) for high-frequency state. | Tie recompositions directly to rapidly emitting state values (audio frames, download bytes) in root composables. |
| Keep motion functional only (press feedback, state transitions). | Add decorative/cinematic motion, scroll-jacking, or marketing-landing patterns. |
| Give every interactive element a `contentDescription` + semantic `role`; use `sp` for text. | Ship a control TalkBack cannot reach or describe. |

## 1. Design Language & Theme

- Material 3, system dynamic color where available; one theme definition for the app — screens never branch on theme internals.
- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- In-app screens are utility UI: quiet, dense, settings-like. The bubble is the product's face — it gets the design care; screens get clarity.

## 2. In-App Screens

- Each screen owns its own `Scaffold` + `TopAppBar` (see `ARCHITECTURE_RULES.md` §4).
- Onboarding is a gate list (overlay permission, accessibility, microphone, model download), each row showing state + a deep-link action; nothing is skippable silently.
- Model manager rows show: engine/model name, size, downloaded state, per-model delete. The recommended default (per the plan's ship-both design) is badged, not preselected silently.

## 3. Bubble Overlay UI

The bubble is rendered by `BubbleOverlayService` (window rules in `ARCHITECTURE_RULES.md` §5.2). Its visual states mirror the service states:

| State | Presentation |
|-------|--------------|
| Idle | Mic pill, resting elevation |
| Listening | Pulsing ring + live RMS-driven amplitude + elapsed time |
| Transcribing | Indeterminate progress treatment (no fake percentages) |
| Done | Brief check affordance, then fade to Idle |
| Error | Red flash/shake; the only sanctioned hard-coded semantic color use |

- RMS/waveform updates arrive via deferred lambda reads (`CODE_STYLE_RULES.md` §4.5) — the bubble must not recompose its whole tree per audio frame.
- Touch targets ≥ 48.dp. The bubble must remain fully on-screen after drag, rotation, and fold/unfold.
- Dismiss (drag to bottom) is a visual state too: the bubble shrinks toward the dismiss zone while dragging.

## 4. Preview Requirements

Every composable file MUST include previews for:
- Light + Dark themes
- Empty state (where applicable)
- Populated / long content
- Loading state (where applicable)

Preview composables must NOT reference Hilt or real ViewModels. Use fake presentation models.

## 5. Accessibility

- All interactive elements must have `contentDescription` + appropriate semantic `role`.
- Use `sp` units for text. System font scaling must be respected.
- TalkBack must work across all in-app screens. (The bubble itself is an overlay control; its actions must also be reachable from the app's main screen for users who cannot use the overlay.)
