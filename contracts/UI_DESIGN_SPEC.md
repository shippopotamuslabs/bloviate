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
- Spacing, shapes, typography, elevation, component defaults, and motion follow the **Compose Gospel**, embedded in full in §6 (v1.3, ENC-86): the `BloviateSpacing` object is the only spacing source in screens and shared components, and the theme carries the gospel type and shape scales. The M3 Expressive motion scheme is **adopted** (ENC-90): the catalog pins material3 1.5.0-beta01, the newest milestone with the Expressive API public, and the theme runs `MotionScheme.expressive()` (see the adoption notes in §6.2 and §6.7).
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

Declared canonical for Bloviate / Stratum / Enclave by the user, 2026-10-19 (Material 3 + M3 Expressive, Android 16 QPR1). Embedded here **in full** under the user's direction (see the v1.3 amendment note): this section plus §1's palette are the complete design law for this repo. Where this section's generic component list mentions a component Bloviate does not use, the spec still stands as the default when that component is introduced.

**Why this works:**
1. 8dp grid + 4dp micro: consistent spacing per Material; everything aligns, no random 5dp/7dp. Layouts feel calm and scannable.
2. 48dp touch targets are law: `Modifier.minimumInteractiveComponentSize()` on custom interactives — even if the visible size is smaller, the hit area auto-expands to 48dp. Accessibility + fat-finger research, not preference.
3. Elevation is tint, not shadow, in dark: shadows become invisible on dark; convey elevation with surface tint — a subtle tonal wash. Inverse rhythm: raised is slightly LIGHTER than base in dark mode.
4. Shape system = hierarchy: nested radius = outer radius − padding (28dp card with 12dp padding holds 16dp corners). Buttons use capsule (radius = half height); cards use fixed radii. Don't invent radii.
5. M3 Expressive is the baseline: morphable shapes (squircle, pill, cookie, clover), spring physics as the default motion scheme, emphasized typography with bigger/heavier display. Shipping old 2023 M3 looks dated.

### 6.1 Spacing & layout

- Base unit: 4dp. Major grid: 8dp — all screen margins, section gaps, card padding are multiples of 8. Micro grid: 4dp — internal icon-to-text, chip gaps.
- Screen margin: 16dp mobile, 24dp tablet. Content max-width: 600dp centered on tablet.
- Vertical rhythm: 8 / 16 / 24 / 32 / 48 / 64. **Never** use 5, 7, 10, 15, etc.
- Implementation: the `BloviateSpacing` object (s4, s8, s12, s16, s24, s32, s48, s64) is the ONLY spacing source in app screens and shared dashboard components. Raw `dp` literals for padding/gaps/margins in presentation code are contract violations. Non-spacing geometry (icon glyph sizes, border/stroke widths, the overlay's waveform measurements) is component-local geometry, not spacing, and is exempt — it is documented where it lives.

### 6.2 Shapes

| Token | Radius | Use |
|-------|--------|-----|
| extraSmall | 4dp | Text field top, chips' small containers, small containers |
| small | 8dp | Default for cards/menus at low emphasis; status chips |
| medium | 12dp | Cards, dialogs, sheets at medium emphasis |
| large | 16dp | Large cards, FAB, medium-emphasis interactive/expandable |
| extraLarge | 28dp | Bottom sheets, large dialogs, hero containers; the recording bar |
| full (pill) | 100% | Buttons, FABs, toggle track, active nav indicator, status pill |

- Nested rule: child radius = parent radius − padding (e.g. 28dp sheet + 12dp padding = 16dp inner card).
- Expressive shapes (Cookie4Sided, Clover, Squircle, Arch) are for loaders, FAB morph, and button press morph; buttons morph pill → 12dp + scale 0.96 on press with high-stiffness springs (pressed shape per the Button Shape Gospel, §6.8). *Adoption note (ENC-90): adopted. The catalog pins material3 1.5.0-beta01 (Compose BOM 2026.09.00), the milestone line whose Expressive classes are public API; `BloviateTheme` is a `MaterialExpressiveTheme` on `MotionScheme.expressive()`, stock buttons inherit the library's pressed-shape morph under it, and the polygon presets (Cookie4Sided, Clover4Leaf, Arch — plus the design system's own Circle and Pill) are exposed as `BloviateMorphShapes` for the first component whose spec calls for a hand-driven morph; no Bloviate component calls for one yet. (The pinned artifact has no preset literally named Squircle; its Square preset is the square member of the family.) The pin diverges from Enclave's catalog under the user's ENC-90 direction — see the v1.4 amendment.*

### 6.3 Typography

Font: Shipopotamus Sans; fallback Roboto Flex / Google Sans Flex variable. *Adoption note (ENC-86): no Shipopotamus Sans asset exists yet, so the theme leaves the family unset — the platform default sans carries the scale below; the brand font lands in the theme definition only, when it ships.*

| Style | Size / line height | Weight | Notes |
|-------|--------------------|--------|-------|
| displayLarge | 57sp / 64sp | 400 | Hero only; letter-spacing −0.25 |
| displayMedium | 45sp / 52sp | 400 | |
| displaySmall | 36sp / 44sp | 400 | |
| headlineLarge | 32sp / 40sp | 400 | |
| headlineMedium | 28sp / 36sp | 400 | |
| headlineSmall | 24sp / 32sp | 400 | |
| titleLarge | 22sp / 28sp | 500 SemiBold (600) | Card titles, top bar; emphasized in Expressive |
| titleMedium | 16sp / 24sp | 500 Medium | List titles, buttons |
| titleSmall | 14sp / 20sp | 500 Medium | |
| labelLarge | 14sp / 20sp | 500 Medium | **Button labels — all buttons use labelLarge** |
| labelMedium | 12sp / 16sp | 500 Medium | |
| labelSmall | 11sp / 16sp | 500 Medium | |
| bodyLarge | 16sp / 24sp | 400 | Main reading |
| bodyMedium | 14sp / 20sp | 400 | Secondary reading; default text-field input |
| bodySmall | 12sp / 16sp | 400 | Captions, supporting text; letter-spacing +0.15 |

- Dark-mode astigmatism rule: **never weight 300 (Light) on dark**. Body is 400–500, emphasis 600.
- The full scale is pinned in the theme (`BloviateTypography`) and asserted by unit tests; components consume `MaterialTheme.typography`, never ad-hoc `TextStyle`s.

### 6.4 Elevation (dark)

Do NOT use drop shadows in dark. Tonal elevation, mapped to the palette's surface ladder (§1):

| Elevation | Surface | Tint |
|-----------|---------|------|
| 0dp | bg-base #0D0E11 | no tint |
| 1dp | surface-1 #181A20 | 5% primary tint overlay |
| 3dp | surface-2 #1F222A | 8% tint — cards on hover/focus |
| 6dp | surface-3 #272A33 | 11% tint — bottom sheets, dialogs |

Use `surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest` tokens; `surfaceTint` = accent. (The bubble overlay's `Surface` elevation is window treatment over other apps, governed by §3, not by this table.)

### 6.5 Component specs (the defaults)

- **Buttons** — Height 40dp; shape full pill; label = labelLarge; padding 24dp text-only / 16dp with icon leading; icon 18dp; gap 8dp. Emphasis order high→low: Filled → Tonal → Elevated → Outlined → Text. Filled: container accent (#7A7CFF) with the bg-base foreground (ENC-85's computed higher contrast: 5.66:1 vs white's 3.41:1); primary CTA uses the Large 56dp height. Tonal: container accent-muted (accent @ 15%), foreground text-primary. Elevated: surface-1 + 1dp tonal elevation. Outlined: 1dp border-default, no fill. Text: 12dp horizontal padding, no container. Icon button: 40×40 + 8dp padding, icon 24dp. Segmented / ToggleButton: height 40dp, padding 24dp, selected uses the tonal container. Press state: corner morph 50% → 12dp + scale 0.96 + ripple, on springs. Button shape language per §6.8: full pill by default; the square variant (12dp medium / 16dp large) only for toolbar/secondary/dense contexts — never mixed randomly in the same row; one screen = one button shape language.
- **FAB** — Small 40dp, medium 56dp, large 96dp (Expressive). Shape large 16dp or full for extended. Container accent, icon 24dp. Extended FAB: height 56dp, labelLarge + 16dp icon gap.
- **Text fields** — Default container height 56dp; padding 8dp vertical, 16dp horizontal without icons / 12dp with icons; icon-to-text gap 16dp; supporting-text top gap 4dp; counter gap 16dp. Filled: container surface-2, shape extraSmall top only (4dp top radius, 0 bottom), active indicator 2dp accent, inactive 1dp border. Outlined: container transparent, outline 1dp border-default, focused 2dp accent, shape extraSmall 4dp all corners, label padding 4dp left/right. Input text bodyLarge; label floats bodySmall → labelSmall; supporting text bodySmall. Never pure-white label — text-secondary. (Stock M3 fields already enforce the 56dp container, the indicator widths, and the bodyLarge input style; the theme's type scale supplies the rest.)
- **Cards** — Elevated: surface-1 + 1dp tonal elevation. Filled: surface-2 flat. Outlined: transparent + 1dp border-default. Shape medium 12dp default, large 16dp for hero cards. Content padding 16dp. On click: ripple + elevate to 3dp. Nested content: inner radius = 12dp − 8dp padding = 4dp.
- **Chips** — All 32dp height, label labelLarge, padding horizontal 16dp, icon gap 8dp. Assist: shape small 8dp, outline 1dp. Filter: shape small, check icon 18dp, selected = tonal container. Input: avatar 24dp + label + close 18dp. Suggestion: tonal container.
- **Switch / toggle** — Track 32dp high × 52dp wide, shape full pill; thumb 24dp; inactive track = border-default color; active track = accent; touch target auto-expands to 48dp. (Stock M3 `Switch` implements this geometry, including the 24dp checked thumb.) Colors are **theme-owned** (`bloviateSwitchColors()`, ENC-88) — stock M3 switch colors on this scheme are banned: they paired an outline-colored thumb with a surfaceVariant track at 1.17:1 and the OFF knob vanished. The gospel pairings: OFF = text-primary thumb (11.7:1 on the track) on the border-default track, rimmed text-tertiary (≥5:1 against both the card surface and bg-base); ON = accent track with the bg-base thumb (5.66:1, the §1 on-accent decision).
- **Checkbox / radio** — 20dp visible, 48dp touch target auto-expanded. Shape small 4dp for checkbox, full for radio. Active = accent.
- **Slider** — Track 4dp height; active track accent; inactive border-default; thumb 20dp; halo 40dp on press; touch target 48dp height.
- **Bottom sheet / dialog** — Bottom sheet: shape extraLarge 28dp top only, container surface-3 at 6dp elevation + 11% tint, drag handle 32×4dp in text-faint, scrim = the palette's bg-scrim. (A Haze sheet instead follows §6.9: `hazeChild` + `ShippopotamusHazeSheet` + surface-3 tint, no opaque scrim.) Dialog: shape extraLarge 28dp, container surface-3, padding 24dp, button-row gap 8dp, title titleLarge.
- **Navigation** — Navigation bar (mobile): height 80dp, 3–5 items, active indicator 32dp height, full pill shape, icon 24dp, label labelMedium. Container = glass per §6.9 (always glass, never solid — supersedes the earlier solid bg-subtle container). TopAppBar: transparent at rest, glass when scrolled, per §6.9. Navigation rail (tablet): width 80dp, same active indicator. TopAppBar small: 64dp height, titleLarge; medium 112dp; large 152dp (Expressive uses bigger, heavier display type for large).
- **List item** — One-line 56dp, two-line 72dp, three-line 88dp. Leading icon 24dp, padding 16dp, gap 16dp. Title titleMedium; subtitle bodyMedium in text-secondary.

### 6.6 Accessibility & motion (non-negotiable)

- Touch: 48×48dp minimum for EVERY interactive element. Use `Modifier.minimumInteractiveComponentSize()` on custom interactives (e.g. dashboard rows); radio/checkbox hit areas expand to 48×48 even when the visible control is smaller. This is Material behavior parity.
- Text: mobile default bodyLarge 16sp is the readable floor with the weights above.
- Motion: the M3 Expressive motion scheme (`MotionScheme.expressive()`) is the theme default — spring physics, not FastOutSlowIn. Standard easing: emphasized for hero, standard for containers, 300–500ms. Shape morphing is a first-class interaction primitive. Keep motion functional (the Strict Boundaries table still bans decorative/cinematic motion — Expressive springs serve press feedback and state transitions, not decoration). *Adoption status: adopted (ENC-90) — the theme provides `MotionScheme.expressive()` via `MaterialExpressiveTheme` (material3 1.5.0-beta01; see the §6.2 adoption note). Stock components that read the theme's motion scheme inherit it; any component the library has not yet migrated to the scheme keeps its built-in springs, and no custom motion language is invented locally. The bubble overlay's former per-transition springs (ENC-93) are retired (ENC-167, §3): the overlay root runs no size animation and is not replaced by the scheme.*
- Haptics: light click on toggle/switch, medium on FAB press, via `HapticFeedback`.

### 6.7 Theme wiring (how §6 reaches code)

- `BloviateTheme` (a `MaterialExpressiveTheme`) provides: the §1 color scheme, the §6.2 shape scale (`BloviateShapes.Scale`, plus `BloviateShapes.Pill`), the §6.3 type scale (`BloviateTypography`), and the motion scheme — `MotionScheme.expressive()` (ENC-90; see the v1.4 amendment for the version facts). The morphable polygon vocabulary (`BloviateMorphShapes`) sits beside the corner scale for components whose spec calls for a morph. The Expressive swap stayed local to the theme definition by design.
- Stock M3 components inherit the gospel defaults from the theme (button 40dp pill + labelLarge, field 56dp, card 12dp tonal, chip geometry where M3 chips are used, switch 52×32/24, navigation bar 80dp + pill indicator + labelMedium). Custom components (dashboard rows/cards, status chip/pill) implement their §6.5 specs directly and draw spacing only from `BloviateSpacing`.
- The bubble overlay (§3) wears the same theme: it consumes the type scale and the shape scale (the recording pill's corner is extraLarge) and the spacing scale where an equivalent exists; its window geometry (bubble/row sizes, waveform measurements) is overlay-specific and stays defined in the overlay code.

### 6.8 Button Shape Gospel — Canonical (v1.5, 2026-10-20)

Declared canonical by the user, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. Embedded here **in full** under the user's direction, like §1 and §6: this contract is self-contained. This section governs button shape specifically and supersedes §6.2/§6.5 wherever they disagree on button shape or button press morph.

**Default for Bloviate / consumer: FULL PILL**

- Shape: `CircleShape` / 9999px / full token (`ShippopotamusButtonShape`).
- Why: empirically friendliest, highest tap recognition, matches Google Phone, Gboard, etc. More rounded corners + pill buttons are the M3 signature.

**Allowed variant for toolbars / secondary / dense enterprise: SQUARE**

- Shape: `RoundedCornerShape(12.dp)` for medium buttons, `RoundedCornerShape(16.dp)` for large (`ShippopotamusButtonShapeSquare`).
- When to use: inside a card that already has 12dp radius — use an 8dp inner button (nested radius = outer − padding); data-dense toolbar, filter bar, table actions; when you want the Linear/Raycast serious vibe vs. bubbly consumer.

**Rules (non-negotiable)**

- Never mix randomly in the same row. One screen = one button shape language.
- If you use the square variant, it still morphs on press to the same pressed shape as the pill variant: `ShippopotamusPressedShape = RoundedCornerShape(12.dp)`.

**Compose implementation**

```kotlin
val ShippopotamusButtonShape = CircleShape // DEFAULT PILL
val ShippopotamusButtonShapeSquare = RoundedCornerShape(12.dp) // ALLOWED VARIANT (medium; 16.dp for large)
val ShippopotamusPressedShape = RoundedCornerShape(12.dp) // Both morph to this on press

Button(
  shape = ShippopotamusButtonShape,
  modifier = Modifier.minimumInteractiveComponentSize() // 48dp enforced
) { Text("Primary", style = MaterialTheme.typography.labelLarge) }

// Expressive press morph
val corner by animateDpAsState(if (pressed) 12.dp else 100.dp, spring(StiffnessHigh))
val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
```

*Adoption note for this repo:* Bloviate's stock buttons already inherit the pill default from the theme (`BloviateShapes.Pill = CircleShape`, §6.7), so the default needs no code change. The tokens above name the contract-level shapes; wiring them as named theme tokens, and any square-variant use, is implementation work under a future ticket — this amendment changes the contract, not shipped screens.

### 6.9 Glass Bars Gospel — Top + Bottom + Haze (v1.6, 2026-10-20)

Declared canonical by the user, 2026-10-20, for Bloviate / Stratum / Enclave and all future Shippopotamus apps. Uses **Haze 2.0** (Chris Banes); Android 15+ edge-to-edge enforced. Embedded here **in full** under the user's direction, like §6.8: this contract is self-contained. This section governs bars, sheets, and overlay glass, and supersedes §6.5 wherever they disagree on those surfaces.

**Dependency**

```kotlin
implementation("dev.chrisbanes.haze:haze:2.0.1") // core blur
implementation("dev.chrisbanes.haze:haze-glass:2.0.1") // refraction Glass (optional, for hero)
```

Haze provides hardware-accelerated visual effects — blur, refraction-driven Glass, and custom effects with reusable config.

**Foundation — edge-to-edge (required for glass to work)**

- Activity: `enableEdgeToEdge()` — status + gesture nav are transparent, content draws behind.
- Scaffold: `contentWindowInsets = WindowInsets(0,0,0,0)` — insets are handled manually.
- Main body: `Modifier.hazeSource(state)` + `.windowInsetsPadding` + `.consumeWindowInsets`.

**Reusable styles — one `HazeState` per screen, shared; don't inline magic numbers**

```kotlin
val hazeState = rememberHazeState()

val ShippopotamusHazeTopBar = HazeStyle.Unspecified.copy(
  blurRadius = 24.dp, // 20–28dp for top bar — enough to read title over photos
  tint = Color(0xFF0D0E11).copy(alpha = 0.65f), // bg-base #0D0E11 at 65%
  noiseFactor = 0.5f, // micro-texture prevents banding on OLED
)

val ShippopotamusHazeBottomBar = HazeStyle.Unspecified.copy(
  blurRadius = 20.dp,
  tint = Color(0xFF181A20).copy(alpha = 0.75f), // surface-1 #181A20 at 75%
  noiseFactor = 0.5f
)

val ShippopotamusHazeSheet = HazeStyle.Thick // preset: heavy blur for bottom sheets
```

Usage pattern: background scrollable = `hazeSource`; bars = `hazeChild`; **never put haze behind body text directly — behind containers only.**

**Top bar (TopAppBar)** — Height small 64dp (default), medium 112dp, large 152dp for hero. Container TRANSPARENT when at top (scroll offset 0), becomes glass when scrolled. Shape: none — full width, no rounded corners. Content: title `titleLarge`, icons 24dp, padding 16dp. Border: none — blur provides separation. When scrolled: glass + subtle 1dp bottom hairline #2A2E39 at 40%, or borderless for pure glass. Content scrolls underneath, blurred — movement is visible but the title stays readable.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippopotamusTopBar(
  title: String,
  hazeState: HazeState,
  scrollBehavior: TopAppBarScrollBehavior
) {
  val isScrolled = scrollBehavior.state.overlappedFraction > 0.01f

  TopAppBar(
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = { IconButton({}) { Icon(Icons.Default.Menu, null) } },
    actions = { IconButton({}) { Icon(Icons.Default.Search, null) } },
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.Transparent, // transparent when not scrolled
      scrolledContainerColor = Color.Transparent // we handle via haze
    ),
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = if (isScrolled) ShippopotamusHazeTopBar else HazeStyle.Unspecified.copy(blurRadius = 0.dp)
      )
      .then(
        if (isScrolled) Modifier.drawWithContent {
          drawContent()
          drawLine(Color(0xFF2A2E39).copy(0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        } else Modifier
      )
  )
}

// In screen:
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior() // or enterAlwaysScrollBehavior()
Scaffold(
  modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  topBar = { ShippopotamusTopBar("Bloviate", hazeState, scrollBehavior) },
  containerColor = Color(0xFF0D0E11),
  contentWindowInsets = WindowInsets(0,0,0,0)
) { padding ->
  LazyColumn(
    Modifier
      .fillMaxSize()
      .hazeSource(state = hazeState, zIndex = 0f) // zIndex 0 = source
      .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
      .padding(padding)
  ) { /* main body passes underneath top bar */ }
}
```

**Bottom bar (NavigationBar)** — Height 80dp (68dp tighter is allowed, 80dp is canonical). Container ALWAYS glass — never solid; translucent tint + blur so content scrolls under. Shape: none for the bar; active indicator = full pill 32dp height, accent #7A7CFF at 15% fill. Icons 24dp, label `labelMedium`. Inactive #858994; active #EDEEF0 + accent indicator. Border: 100% borderless — blur is the separation. Gesture nav: `windowInsetsPadding(WindowInsets.navigationBars)` + `navigationBarsPadding` on content so the last item is not hidden behind the system pill. The bottom bar shares the screen's `hazeState` with the top bar so both blur the same source.

```kotlin
@Composable
fun ShippopotamusBottomBar(hazeState: HazeState) {
  var selected by remember { mutableStateOf(0) }

  NavigationBar(
    containerColor = Color.Transparent, // haze handles color
    tonalElevation = 0.dp,
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = ShippopotamusHazeBottomBar
      )
      .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
  ) {
    listOf("Home", "Search", "Library").forEachIndexed { i, label ->
      NavigationBarItem(
        selected = i == selected,
        onClick = { selected = i },
        icon = { Icon(Icons.Default.Home, null) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = Color(0xFFEDEEF0),
          selectedTextColor = Color(0xFFEDEEF0),
          indicatorColor = Color(0xFF7A7CFF).copy(alpha = 0.15f), // accent-muted
          unselectedIconColor = Color(0xFF858994),
          unselectedTextColor = Color(0xFF858994)
        )
      )
    }
  }
}
```

**Where else to use Haze**

1. Bottom sheet: sheet container = `hazeChild` with `ShippopotamusHazeSheet` + surface-3 #272A33 tint. Content underneath stays visible blurred — elevated without an opaque scrim.
2. FAB over feed: extended FAB that blurs the feed behind it — small blur, 12dp.
3. Image overlays: hero image with a glass chip (e.g. a "Live" badge) — haze-glass with refraction for liquid glass.

**Performance:** Haze 2.0 adaptive rendering quality automatically drops quality on a low-end GPU. Keep `blurRadius <= 28dp`; avoid stacking more than 2 `hazeChild`s in the same frame.

*Adoption note for this repo:* Bloviate has **no Haze dependency yet** and its in-app screens are dashboard-form utility UI (§1/§2); the bubble overlay is not a Haze surface. Adding the dependency, the edge-to-edge foundation, and glass bars is implementation work under a future ticket — this amendment changes the contract, not shipped screens. The top-bar title consumes `MaterialTheme.typography.titleLarge` (theme-owned, §6.3 SemiBold); the user's pasted spec line saying "Medium" is superseded by the theme scale.
