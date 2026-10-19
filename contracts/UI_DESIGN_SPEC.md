# UI_DESIGN_SPEC.md
**Bloviate – UI Design Contract v1.2**
(Adapted from Enclave `UI_DESIGN_SPEC.md` v2.6 — its theme-token, preview, and rigor rules; Enclave's product-specific theme families and chat shapes do not apply here.)

> **v1.1 amendment (2026-10-19, ENC-75, under the user's explicit direction):**
> the in-app theme is **dark-only** and the in-app screens follow a
> dashboard design language (§1, §2). The user directed both changes
> verbatim ("It should just be dark themed… I want it to look more like
> a good polished dashboard"), which is the authorization for editing
> this contract's theme sections in the ENC-75 MRs.

> **v1.2 amendment (2026-10-19, ENC-85, under the user's explicit
> direction — his Contract Waiver for this edit):** the app's colors are
> now the **Shippopotamus Palette Gospel**, declared canonical for all
> Shippopotamus apps by the user on 2026-10-19. The full palette is
> embedded in §1 so this contract is self-contained; where any mockup
> or generated image disagrees with the written hexes, the written
> hexes win. The bubble overlay now wears the same fixed palette (it no
> longer follows the system theme), and the semantic colors are theme
> tokens — the former hard-coded-error-color exception is retired.

UI contract for Bloviate's two surfaces: the in-app screens (onboarding, settings, model manager) and the floating bubble. Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation patterns and the overlay service rules).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use the app theme + Material 3 semantic color roles everywhere. | Hard-code hex colors outside the theme definition. |
| Include `@Preview` for shared components covering the **Dark** theme's states. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`, `() -> Float` for RMS/progress) for high-frequency state. | Tie recompositions directly to rapidly emitting state values (audio frames, download bytes) in root composables. |
| Keep motion functional only (press feedback, state transitions). | Add decorative/cinematic motion, scroll-jacking, or marketing-landing patterns. |
| Give every interactive element a `contentDescription` + semantic `role`; use `sp` for text. | Ship a control TalkBack cannot reach or describe. |

## 1. Design Language & Theme

- Material 3; **one fixed dark color scheme for the whole app** — dark-only (v1.1 amendment, ENC-75): no light scheme, no dynamic color, screens never branch on theme internals. Hex values live only in the theme definition.
- The bubble overlay wears the same fixed scheme (v1.2, ENC-85): it renders over other apps but no longer follows the system theme — one canonical palette for screens and overlay alike.

### Canonical palette — the Shippopotamus Palette Gospel (v1.2, ENC-85)

Declared canonical for all Shippopotamus apps by the user, 2026-10-19. Gospel rules: no pure black/white (halation) — canvas #0D0E11 with primary text #EDEEF0 at 15.8:1; elevation by lightness steps, never shadows; accents desaturated (≤60% saturation) so they don't vibrate on dark; secondary text stays WCAG AA.

| Token | Hex | Role |
|-------|-----|------|
| bg-base | #0D0E11 | App canvas (M3 `background`, `surfaceContainerLowest`) |
| bg-subtle | #111216 | Navbar / tab bar (`surfaceContainerLow`, tab bar container) |
| surface-1 | #181A20 | Cards, sheets (M3 `surface`, `surfaceContainer`) |
| surface-2 | #1F222A | Elevated / pressed (`surfaceContainerHigh`, `surfaceVariant`) |
| surface-3 | #272A33 | Hover, modal, popover (`surfaceContainerHighest`) |
| border-default | #2A2E39 | M3 `outline` |
| border-strong | #343A48 | Extended token (emphasized borders) |
| border-subtle | #20222E | M3 `outlineVariant` |
| text-primary | #EDEEF0 | M3 `onBackground` / `onSurface` |
| text-high | #C5C7DD | Secondary headings; `onPrimaryContainer` |
| text-secondary | #A1A5B0 | M3 `onSurfaceVariant` |
| text-tertiary | #858994 | Captions, metadata (extended token) |
| text-faint | #6E7280 | Icons, disabled, placeholder (extended token) |
| accent | #7A7CFF | M3 `primary` |
| accent-hover | #8D8FFF | M3 `secondary`; hover states |
| accent-active | #5F62B9 | Pressed/active states (extended token) |
| accent-border-subtle | #4B4EBF | Extended token |
| accent-deep | #3B3BAA | M3 `inversePrimary` |
| accent-muted-bg | accent @ 15% alpha | Tinted accent surfaces |
| focus-ring | accent @ 40% alpha | Focus indication |
| success | #7DBE8C | M3 `tertiary`; status POSITIVE; bubble Done |
| warning | #D9B07A | Status ATTENTION (extended semantic) |
| danger | #E07A7A | M3 `error`; bubble Error + dismiss treatments |
| info | #7BB5D6 | Status ACCENT (extended semantic) |
| bg-scrim | bg-base @ 80% alpha | Bottom-sheet scrim |
| surface-overlay | #1F222AF2 | Floating surface over content |

Foregrounds on bright gospel surfaces (accent, semantics) are bg-base — computed, it beats white on every one (accent 5.66:1 vs 3.41:1). Container tints for the accent and semantics are exact alpha composites over their base surface (accent @ 30% over bg-base; semantics @ 16% over surface-1), kept opaque so overlay surfaces never turn translucent. The four semantic colors are first-class theme colors (`BloviateSemanticColors`), consumed by the status chips/pills and the bubble's Done/Error states.
- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- In-app screens are utility UI in **dashboard form** (v1.1, ENC-75): a screen's verdict fits in a header status pill; state is shown as chips, never as sentences; the control matches the job (switch for on/off, segmented control for a small exclusive choice, radio list for a longer one, the row itself as a settings deep-link); at most one hint line per row; explanations live in one-line footers, not paragraphs. The bubble is still the product's face — screens get clarity, now with polish.

## 2. In-App Screens

- Each screen owns its own `Scaffold` + `TopAppBar` (see `ARCHITECTURE_RULES.md` §4). A screen with an overall verdict puts it in the app bar as a status pill.
- Sections are small-caps headers over one grouped card of rows (icon tile + label + one sub-line + the control), not one card per control.
- Onboarding is a gate list (overlay permission, accessibility, microphone, model download), each row showing state as a chip + a deep-link action; nothing is skippable silently.
- Model manager rows show: engine/model name, size, downloaded state. The recommended default (per the plan's ship-both design) is badged, not preselected silently.

## 3. Bubble Overlay UI

The bubble is rendered by `BubbleOverlayService` (window rules in `ARCHITECTURE_RULES.md` §5.2). Its visual states mirror the service states:

| State | Presentation |
|-------|--------------|
| Idle | Mic pill, resting elevation |
| Listening | Recording bar: live waveform (RMS-driven) + elapsed time |
| Transcribing | Shimmering status text: "Transcribing" (gradient sweep over the word; no spinner, no fake percentages) |
| Polishing | Shimmering status text: "Polishing" — only while an AI style pass runs; local-only sends skip this state |
| Done | Brief check affordance, then fade to Idle |
| Error | Danger-token flash/shake (theme semantic color, §1) |

- RMS/waveform updates arrive via deferred lambda reads (`CODE_STYLE_RULES.md` §4.5) — the bubble must not recompose its whole tree per audio frame.
- Touch targets ≥ 48.dp. The bubble must remain fully on-screen after drag, rotation, and fold/unfold.
- Dismiss (drag to bottom) is a visual state too: the bubble shrinks toward the dismiss zone while dragging.

## 4. Preview Requirements

Every composable file MUST include previews for:
- The Dark theme (the only theme since v1.1)
- Empty state (where applicable)
- Populated / long content
- Loading state (where applicable)

Preview composables must NOT reference Hilt or real ViewModels. Use fake presentation models.

## 5. Accessibility

- All interactive elements must have `contentDescription` + appropriate semantic `role`.
- Use `sp` units for text. System font scaling must be respected.
- TalkBack must work across all in-app screens. (The bubble itself is an overlay control; its actions must also be reachable from the app's main screen for users who cannot use the overlay.)
