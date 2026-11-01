# UI_DESIGN_SPEC.md
**Bloviate – UI Design Contract v1.8**
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

> **v1.3 amendment (2026-10-19, ENC-86, under the user's explicit
> direction — his Contract Waiver for this edit):** the app's spacing,
> shapes, typography, elevation, component defaults, and motion now
> follow the **Shippopotamus Compose Gospel**, declared canonical for
> Bloviate / Stratum / Enclave by the user on 2026-10-19. The full
> Compose Gospel is embedded in §6 so this contract is self-contained
> (§1 carries the Palette Gospel in full): together they are the whole
> design law for this repo, enforceable by whoever works here, with no
> external document to lose. The user directed both gospels verbatim:
> "update all repository UI style contracts to contain all of this
> information so we never lose it and it gets enforced no matter where
> I work on things."
>
> **v1.4 amendment (2026-10-19, ENC-90, under the user's explicit
> direction — his fast-follow approval):** the M3 Expressive adoption
> staged in v1.3 has landed. The version catalog pins material3
> **1.5.0-beta01** — the newest milestone whose Expressive API
> (`MotionScheme`, `MaterialExpressiveTheme`, the morphable
> `MaterialShapes`) is public; the latest stable (1.4.0) still marks
> that API internal, and the latest Compose BOM (2026.09.00, also
> adopted) still maps material3 to 1.4.0, so the milestone is pinned
> explicitly in the catalog. `BloviateTheme` is now a
> `MaterialExpressiveTheme` with `MotionScheme.expressive()` as the
> app's motion source, and the morphable polygon vocabulary lives in
> `BloviateMorphShapes` beside the (unchanged) gospel shape scale.
> The bubble overlay's per-transition springs (ENC-93) are untouched.
> This repo's pins now deliberately diverge from Enclave's catalog
> (BOM 2026.05.01 / material3 1.4.0), flagged for the user's
> ratification on ENC-90; Enclave's own bump is a separate follow-up.

> **v1.5 amendment (2026-10-20, under the user's explicit
> direction — his Contract Waiver for this edit):** button shape is
> now governed by the **Shippopotamus Button Shape Gospel**, declared
> canonical by the user on 2026-10-20 for Bloviate / Stratum /
> Enclave. The full gospel is embedded in §6.8 so this contract stays
> self-contained. Bloviate is the consumer case: the default is the
> **full pill**. The gospel supersedes §6.2/§6.5 on one point: the
> button press morph now lands at **12dp** (was 16dp) — both the pill
> default and the allowed square variant morph to the same 12dp
> pressed shape. The user's pasted source spelled the tokens
> `Shipopotamus*` (one p); the canonical spelling is
> `Shippopotamus*`, used throughout §6.8.

> **v1.6 amendment (2026-10-20, under the user's explicit
> direction — his Contract Waiver for this edit):** bars, sheets,
> and overlay glass are now governed by the **Shippopotamus Glass
> Bars Gospel** (Haze 2.0), declared canonical by the user on
> 2026-10-20 for Bloviate / Stratum / Enclave. The full gospel is
> embedded in §6.9 so this contract stays self-contained. It
> supersedes §6.5 on two points: the bottom navigation bar is
> **always glass, never solid** (was a solid bg-subtle container),
> and a Haze bottom sheet uses the Thick glass style instead of
> the opaque scrim treatment. Bloviate has no Haze dependency yet;
> §6.9 is contract law for when bars/sheets are built or restyled,
> not a claim about shipped code. The user's pasted source spelled
> the tokens `Shipopotamus*` (one p); the canonical spelling is
> `Shippopotamus*`, used throughout §6.9.

> **v1.7 amendment (2026-10-22, ENC-167, under the user's explicit
> direction — his authorization for this edit):** the bubble overlay no
> longer morphs. The recording layout is separate elements that swap
> instantly (no size animation), placement is one-shot from known
> widths, and the recording row has no drag gesture. §3 below is the
> amended text; the ENC-89/ENC-93 morph and per-transition springs are
> retired for the overlay. (The v1.4 note's line that those springs are
> "untouched" is superseded by this amendment for the bubble overlay.)

> **v1.8 amendment (2026-10-23, BLO-152, under the user's explicit
> direction — his Contract Waiver for this edit):** the v1.7 note's
> clause "the recording row has no drag gesture" is superseded.
> Recording-row drag is restored to the ENC-162 decision ("drag stays
> enabled in every bar state") and BLO-122 free placement: the whole
> recording row drags on raw screen coordinates, and a bar-form drag end
> re-anchors subsequent placements. ENC-167 deleting the drag was an
> accidental regression, fixed in BLO-152. Everything else in v1.7
> (separate elements, instant swap, no morph, one-shot placement)
> stands, with drag as the stated exception in §3.

UI contract for Bloviate's two surfaces: the in-app screens (onboarding, settings, model manager) and the floating bubble. Always enforce together with `ARCHITECTURE_RULES.md` (which defines the structural Scaffold/navigation patterns and the overlay service rules).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use the app theme + Material 3 semantic color roles everywhere. | Hard-code hex colors outside the theme definition. |
| Draw every padding/gap/margin in screens and shared components from `BloviateSpacing`, and shapes/type styles from the theme (§6). | Write raw `dp` spacing literals or off-rhythm values (5, 7, 10, 15, …) in presentation code. |
| Keep every interactive element at ≥ 48dp touch target (`minimumInteractiveComponentSize()` on custom interactives) (§6). | Use Light (300) text weight on dark, or drop shadows for dark-mode elevation. |
| Include `@Preview` for shared components covering the **Dark** theme's states. | Inject real ViewModels or Hilt dependencies into `@Preview` functions. |
| Use lambda-based deferred state reads (e.g., `Modifier.offset { ... }`, `() -> Float` for RMS/progress) for high-frequency state. | Tie recompositions directly to rapidly emitting state values (audio frames, download bytes) in root composables. |
| Keep motion functional only (press feedback, state transitions). | Add decorative/cinematic motion, scroll-jacking, or marketing-landing patterns. |
| Give every interactive element a `contentDescription` + semantic `role`; use `sp` for text. | Ship a control TalkBack cannot reach or describe. |

## 1. Design Language & Theme

- Material 3; **one fixed dark color scheme for the whole app** — dark-only (v1.1 amendment, ENC-75): no light scheme, no dynamic color, screens never branch on theme internals. Hex values live only in the theme definition.
- Spacing, shapes, typography, elevation, component defaults, and motion follow the **Compose Gospel** (§6; the gospel text lives in `contracts/gospel/COMPOSE-GOSPEL.md` since GLO-13): the `BloviateSpacing` object is the only spacing source in screens and shared components, and the theme carries the gospel type and shape scales. The M3 Expressive motion scheme is **adopted** (ENC-90): the catalog pins material3 1.5.0-beta01, the newest milestone with the Expressive API public, and the theme runs `MotionScheme.expressive()` (see the adoption notes in §6.2 and §6.7).
- The bubble overlay wears the same fixed scheme (v1.2, ENC-85): it renders over other apps but no longer follows the system theme — one canonical palette for screens and overlay alike.

### Canonical palette — the Shippopotamus Palette Gospel (v1.2, ENC-85)

The palette gospel text lives in the gospels submodule:
`contracts/gospel/PALETTE.md` — the token table, the rationale (no pure
black/white, elevation by lightness steps, desaturated accents, WCAG AA
secondary text), and the on-accent foreground / container-tint
computations. It is no longer embedded here (GLO-13). The §1 bullets
above carry Bloviate's local theme decisions (dark-only; the bubble
overlay wears the same fixed scheme); hex values live only in the theme
definition and the gospel.

- Unidirectional data flow. Full state hoisting. Minimal recompositions.
- In-app screens are utility UI in **dashboard form** (v1.1, ENC-75): a screen's verdict fits in a header status pill; state is shown as chips, never as sentences; the control matches the job (switch for on/off, segmented control for a small exclusive choice, radio list for a longer one, the row itself as a settings deep-link); at most one hint line per row; explanations live in one-line footers, not paragraphs. The bubble is still the product's face — screens get clarity, now with polish.

## 2. In-App Screens

- Each screen owns its own `Scaffold` + `TopAppBar` (see `ARCHITECTURE_RULES.md` §4). A screen with an overall verdict puts it in the app bar as a status pill.
- Sections are small-caps headers over one grouped card of rows (icon tile + label + one sub-line + the control), not one card per control.
- Onboarding is a gate list (overlay permission, accessibility, microphone, battery exemption, model download), each row showing state as a chip + a deep-link action; nothing is skippable silently.
- The first-run walk (ENC-67) presents that gate list as a seven-step wizard — welcome, accessibility, overlay, microphone, battery exemption, model download with the engine choice, and a guided test dictation into an in-app scratch field the real bubble serves. It reads the same live gate state as the Setup dashboard (a step's chip is the dashboard's chip), persists its progress so an interrupted walk resumes in place, is skippable from every step by an explicit, always-visible action, and is re-runnable from Setup.
- Model manager rows show: engine/model name, size, downloaded state. The recommended default (per the plan's ship-both design) is badged, not preselected silently.
- Component geometry follows the §6 defaults: cards 12dp tonal, text fields 56dp, chips 32dp, switch 52×32 track with 24dp thumb, navigation bar 80dp with a 32dp pill active indicator, list rows at the gospel heights.

## 3. Bubble Overlay UI

The bubble is rendered by `BubbleOverlayService` (window rules in `ARCHITECTURE_RULES.md` §5.2). Its visual states mirror the service states:

| State | Presentation |
|-------|--------------|
| Idle | Mic pill, resting elevation |
| Warming | Spinner in the bubble's content color while the speech engine finishes its initial warm-up; the bubble appears on schedule instead of staying absent — taps during warm-up are clean no-ops || Listening | Recording row of separate elements: [X bubble] [waveform + elapsed pill] [check bubble] (RMS-driven waveform) |
| Transcribing | Shimmering status text: "Transcribing" (gradient sweep over the word; no spinner, no fake percentages) |
| Polishing | Shimmering status text: "Polishing" — only while an AI style pass runs; local-only sends skip this state |
| Done | Brief check affordance, then fade to Idle |
| Error | Danger-token flash/shake (theme semantic color, §1) |

- RMS/waveform updates arrive via deferred lambda reads (`CODE_STYLE_RULES.md` §4.5) — the bubble must not recompose its whole tree per audio frame.
- The recording layout is **separate elements, swapped instantly** (ENC-167): a row of separate surfaces wearing the user's appearance (shape/tint/opacity) — [X bubble] [waveform + elapsed pill] [check bubble] — with spacing between them. The check bubble carries `primary`/`onPrimary` directly on its outer surface (the check glyph sits centered on it, exactly like the X glyph — no nested inner circle); in the dismiss zone both X and check swap to the error-container colors. No element morphs: there is no `animateContentSize`, no size spring, and no `AnimatedVisibility` size/expand transition on the overlay root; every state change is an instant content swap (at most a fade on appearing elements). X and check are **tap targets arbitrated on the same single pointer stream as the row's drag**: the whole row drags on raw screen coordinates (ENC-162's mechanism — never window-local deltas, which jitter in the self-moving overlay window); released within touch slop it is a tap hit-tested against the X slot (left) and the check slot (right), with the center a no-op; past the slop it is a drag and never fires X/check. X/check keep `Role.Button`, content descriptions, and semantics `onClick` actions so TalkBack is unchanged. (Idle drag, the bottom dismiss zone, and position persistence are unchanged.)
- **Free placement, side from the anchor.** There is no snap-to-edge or dock. The persisted anchor fractions are unchanged. Expansion side is a pure function of the anchor: an anchor whose center is left of the screen center becomes the X and the row unfolds rightward; any other anchor becomes the check and the row unfolds leftward.
- **One-shot placement from known sizes.** Every bar-form state's window position is computed once, before its content composes, from the fixed element widths (slot sizes + waveform width + padding constants) — never from a measured live view width, and never corrected per frame. The window-content edge sitting at the anchor's spot does not move across Recording, Processing, and Done (right-side anchors: the content's right edge stays on the anchor's right edge; left-side mirrored). The single placement exception is one pre-computed fit shift, applied once before the first frame of the new state, when the row would overflow near the screen center. The user-drag exception (BLO-152): absent a user drag the anchor edge does not move; while a drag is active the drag controller is the sole writer of the window layout (ENC-154); a bar-form drag end shifts the anchor by the window's own displacement from its last one-shot place — clamped on-screen for the bubble size and persisted normalized against the BUBBLE size, never the row width — pushes no layout, and subsequent bar-form placements and the return to Idle compute from the dragged anchor. A window resize during recording does not move the window.
- The processing pill **hugs its label** (ENC-89, placement per ENC-167): Transcribing/Polishing carry no waveform, no side bubbles, and no buttons, so their width is the stage word plus s24 horizontal padding — never the recording row's wide skeleton — showing the stage label with the ENC-76 shimmer. It is placed by the same one-shot anchor-edge rule. Done/Error are a **single bubble at the anchor spot**, unchanged.
- Touch targets ≥ 48.dp. The bubble must remain fully on-screen after drag, rotation, and fold/unfold.
- Dismiss (drag to bottom) is a visual state too: the bubble shrinks toward the dismiss zone while dragging.
- Bubble appearance is user-configurable in Setup's Bubble look card (ENC-68), persisted as a versioned preference and applied to the showing bubble live: **opacity** 40–100% (the floor keeps the bubble findable against host apps; the recording bar and processing pill inherit it), **size** in three steps — S 48dp / M 56dp / L 64dp, the bar scaling with the bubble (its cancel/send slots clamp at the 48dp touch-target floor, never below it, so at S they sit flush in the 48dp bar) — **silhouette** (pill, squircle, and the §6.2 expressive polygons cookie / clover / arch; the bar takes corner language from the choice, not the polygon silhouette), and **tint** from the §1 tokens only (indigo default; neutral; the four semantic tones with their containers). No freeform color picker. State treatments outrank the tint: the dismiss zone stays danger, Done stays success, Error stays danger. Placement math (`BubblePositionMath`) is size-aware: the persisted anchor is normalized, drag reads the live view size, and a size change re-clamps the bubble on-screen.

## 4. Preview Requirements

Every composable file MUST include previews for:
- The Dark theme (the only theme since v1.1)
- Empty state (where applicable)
- Populated / long content
- Loading state (where applicable)

Preview composables must NOT reference Hilt or real ViewModels. Use fake presentation models.

## 5. Accessibility

- All interactive elements must have `contentDescription` + appropriate semantic `role`.
- 48×48dp minimum touch target for **every** interactive element is law (§6); custom interactives carry `minimumInteractiveComponentSize()` so the hit area expands even when the visual is smaller.
- Use `sp` units for text. System font scaling must be respected.
- TalkBack must work across all in-app screens. (The bubble itself is an overlay control; its actions must also be reachable from the app's main screen for users who cannot use the overlay.)

## 6. The Compose Gospel — Canonical UI System (v1.3, ENC-86)

Declared canonical for Bloviate / Stratum / Enclave by the user, 2026-10-19 (Material 3 + M3 Expressive, Android 16 QPR1). The gospel text now lives in the gospels submodule — `contracts/gospel/COMPOSE-GOSPEL.md`, with §6.8 and §6.9 pointing at their own gospels — and is no longer embedded here (GLO-13, 2026-10-25). Together with §1's palette gospel, it is the complete design law for this repo. Where the gospel's generic component list mentions a component Bloviate does not use, the spec still stands as the default when that component is introduced. This section keeps the §6 numbering: each subsection points at the gospel and carries Bloviate's local adoption notes under it.

### 6.1 Spacing & layout

The spacing canon (4dp base unit, 8dp major grid, screen margins, content max-width, the 8 / 16 / 24 / 32 / 48 / 64 vertical rhythm — never 5, 7, 10, 15) lives in `contracts/gospel/COMPOSE-GOSPEL.md` ("Spacing & layout").

**Bloviate local rule:** the `BloviateSpacing` object (s4, s8, s12, s16, s24, s32, s48, s64) is the ONLY spacing source in app screens and shared dashboard components. Raw `dp` literals for padding/gaps/margins in presentation code are contract violations. Non-spacing geometry (icon glyph sizes, border/stroke widths, the overlay's waveform measurements) is component-local geometry, not spacing, and is exempt — it is documented where it lives.

### 6.2 Shapes

The shape scale (extraSmall 4dp, small 8dp, medium 12dp, large 16dp, extraLarge 28dp, full pill), the nested-radius rule (child radius = parent radius − padding), and the expressive shape vocabulary live in `contracts/gospel/COMPOSE-GOSPEL.md` ("Shapes").

**Bloviate local notes:**
- Buttons morph pill → 12dp + scale 0.96 on press with high-stiffness springs (pressed shape per the Button Shape Gospel, §6.8).
- *Adoption note (ENC-90): adopted. The catalog pins material3 1.5.0-beta01 (Compose BOM 2026.09.00), the milestone line whose Expressive classes are public API; `BloviateTheme` is a `MaterialExpressiveTheme` on `MotionScheme.expressive()`, stock buttons inherit the library's pressed-shape morph under it, and the polygon presets (Cookie4Sided, Clover4Leaf, Arch — plus the design system's own Circle and Pill) are exposed as `BloviateMorphShapes` for the first component whose spec calls for a hand-driven morph; no Bloviate component calls for one yet. (The pinned artifact has no preset literally named Squircle; its Square preset is the square member of the family.) The pin diverges from Enclave's catalog under the user's ENC-90 direction — see the v1.4 amendment.*

### 6.3 Typography

The full M3 type scale (sizes, line heights, weights) lives in `contracts/gospel/COMPOSE-GOSPEL.md` ("Typography"). The dark-mode astigmatism rule stands: **never weight 300 (Light) on dark** — body is 400–500, emphasis 600.

**Bloviate local notes:**
- Font: Shipopotamus Sans; fallback Roboto Flex / Google Sans Flex variable. *Adoption note (ENC-86): no Shipopotamus Sans asset exists yet, so the theme leaves the family unset — the platform default sans carries the gospel scale; the brand font lands in the theme definition only, when it ships.*
- The full scale is pinned in the theme (`BloviateTypography`) and asserted by unit tests; components consume `MaterialTheme.typography`, never ad-hoc `TextStyle`s.

### 6.4 Elevation (dark)

Do NOT use drop shadows in dark. The tonal elevation ladder (surface + tint at 0/1/3/6dp, mapped to the §1 palette ladder) lives in `contracts/gospel/COMPOSE-GOSPEL.md` ("Elevation (dark)"). Use `surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest` tokens; `surfaceTint` = accent.

**Bloviate local note:** the bubble overlay's `Surface` elevation is window treatment over other apps, governed by §3, not by the gospel's elevation table.

### 6.5 Component specs (the defaults)

The component defaults — buttons, FAB, text fields, cards, chips, switch/toggle, checkbox/radio, slider, bottom sheet/dialog, navigation, list items — live in `contracts/gospel/COMPOSE-GOSPEL.md` ("Component specs (defaults)"). Button shape language is governed by §6.8; bars and sheets by §6.9 where they disagree with the component specs.

**Bloviate local notes:**
- **Switch colors (ENC-88):** Colors are **theme-owned** (`bloviateSwitchColors()`, ENC-88) — stock M3 switch colors on this scheme are banned: they paired an outline-colored thumb with a surfaceVariant track at 1.17:1 and the OFF knob vanished. The gospel pairings: OFF = text-primary thumb (11.7:1 on the track) on the border-default track, rimmed text-tertiary (≥5:1 against both the card surface and bg-base); ON = accent track with the bg-base thumb (5.66:1, the §1 on-accent decision).

### 6.6 Accessibility & motion (non-negotiable)

The accessibility and motion canon (48×48dp minimum touch target for every interactive element via `minimumInteractiveComponentSize()`, the readable text floor, the M3 Expressive spring motion scheme, haptics) lives in `contracts/gospel/COMPOSE-GOSPEL.md` ("Accessibility & motion"). §5 of this contract carries the repo-level accessibility rules alongside it.

**Bloviate local notes:**
- Motion stays functional only (the Strict Boundaries table bans decorative/cinematic motion): Expressive springs serve press feedback and state transitions, not decoration.
- *Adoption status: adopted (ENC-90) — the theme provides `MotionScheme.expressive()` via `MaterialExpressiveTheme` (material3 1.5.0-beta01; see the §6.2 adoption note). Stock components that read the theme's motion scheme inherit it; any component the library has not yet migrated to the scheme keeps its built-in springs, and no custom motion language is invented locally. The bubble overlay's former per-transition springs (ENC-93) are retired (ENC-167, §3): the overlay root runs no size animation and is not replaced by the scheme.*

### 6.7 Theme wiring (how §6 reaches code)

- `BloviateTheme` (a `MaterialExpressiveTheme`) provides: the §1 color scheme, the §6.2 shape scale (`BloviateShapes.Scale`, plus `BloviateShapes.Pill`), the §6.3 type scale (`BloviateTypography`), and the motion scheme — `MotionScheme.expressive()` (ENC-90; see the v1.4 amendment for the version facts). The morphable polygon vocabulary (`BloviateMorphShapes`) sits beside the corner scale for components whose spec calls for a morph. The Expressive swap stayed local to the theme definition by design.
- Stock M3 components inherit the gospel defaults from the theme (button 40dp pill + labelLarge, field 56dp, card 12dp tonal, chip geometry where M3 chips are used, switch 52×32/24, navigation bar 80dp + pill indicator + labelMedium). Custom components (dashboard rows/cards, status chip/pill) implement their §6.5 specs directly and draw spacing only from `BloviateSpacing`.
- The bubble overlay (§3) wears the same theme: it consumes the type scale and the shape scale (the recording pill's corner is extraLarge) and the spacing scale where an equivalent exists; its window geometry (bubble/row sizes, waveform measurements) is overlay-specific and stays defined in the overlay code.

### 6.8 Button Shape Gospel — Canonical (v1.5, 2026-10-20)

Declared canonical by the user, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. The gospel text lives in the gospels submodule: `contracts/gospel/BUTTON-SHAPE-GOSPEL.md` — the FULL PILL default (`CircleShape`), the allowed SQUARE variant (12dp medium / 16dp large), the one-shape-language-per-screen rule, and the shared 12dp pressed shape with its Compose implementation. It is no longer embedded here (GLO-13). This section governs button shape specifically and supersedes §6.2/§6.5 wherever they disagree on button shape or button press morph.

*Adoption note for this repo:* Bloviate's stock buttons already inherit the pill default from the theme (`BloviateShapes.Pill = CircleShape`, §6.7), so the default needs no code change. The gospel tokens name the contract-level shapes; wiring them as named theme tokens, and any square-variant use, is implementation work under a future ticket — this amendment changes the contract, not shipped screens.

### 6.9 Glass Bars Gospel — Top + Bottom + Haze (v1.6, 2026-10-20)

Declared canonical by the user, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. Uses **Haze 2.0** (Chris Banes); Android 15+ edge-to-edge enforced. The gospel text lives in the gospels submodule: `contracts/gospel/GLASS-BARS-GOSPEL.md` — the Haze 2.0.1 dependency pins, the edge-to-edge foundation, the reusable Haze styles, and the top bar / bottom bar / sheet specs (the bottom navigation bar is always glass, never solid). It is no longer embedded here (GLO-13). This section governs bars, sheets, and overlay glass, and supersedes §6.5 wherever they disagree on those surfaces.

*Adoption note for this repo:* adopted (BLO-2) — the Haze 2.0.1 dependency is in `app/build.gradle.kts` and the glass bars are implemented in `GlassBars.kt` / `BloviateTheme.kt`. Bloviate's in-app screens are dashboard-form utility UI (§1/§2); the bubble overlay is not a Haze surface. The top-bar title consumes `MaterialTheme.typography.titleLarge` (theme-owned, §6.3 SemiBold); the user's pasted spec line saying "Medium" is superseded by the theme scale.
