# VanRides Operational Design System Guide

Status: ACTIVE BASELINE
Date: 2026-06-19
Related plan: `UX-UI-Complete-Improvement-Plan-2026-06-19.md`
Related card: `UX-0002 - Design system operacional dark/liquid`

## 1. Purpose

This guide defines the product-level visual rules for the VanRides app and
admin portal. It is intentionally practical: every future screen should be able
to decide which surface, color, motion, copy density and accessibility fallback
to use without inventing a new visual language.

The target experience is:
- reliable;
- premium;
- useful;
- minimal;
- dark by default;
- map/context-first when location matters;
- liquid/glass only when it preserves context.

## 2. Impact Analysis

### Context

The product already has `VanRidesDesignTokens`, `VanRidesMotionTokens`,
`VanRidesLiquidSurface`, `VanRidesLiquidTabScaffold` and admin portal colors.
The problem was semantic drift: mobile and portal could still use separate
colors, radii, heights and motion names even when they represented the same
product meaning.

### Technical changes

- Add semantic token aliases for background, glass, border, state colors,
  spacing, radii, controls and icon sizes.
- Add operational motion aliases for button press, status update, detent settle
  and route refresh.
- Align `AdminPortalColors` with `VanRidesDesignTokens` for core semantics.
- Make `VanRidesLiquidSurface` use the shared semantic radii and glass color.
- Add tests that pin these contracts.

### Affected contracts

No backend or HTTP contract changed. This is a frontend design-system contract.

### Risks and mitigation

- Risk: portal contrast changes after color alignment.
  - Mitigation: keep the portal dark surface family and run admin shell tests.
- Risk: glass becomes decorative and overused.
  - Mitigation: usage rules below restrict glass to contextual overlays.
- Risk: motion becomes uncomfortable or inaccessible.
  - Mitigation: `VanRidesMotionPolicy` keeps reduced-motion fallbacks.

### Minimum validation

- `flutter test test/app/theme/vanrides_theme_test.dart test/app/theme/vanrides_motion_test.dart test/app/widgets/vanrides_liquid_surface_test.dart test/app/widgets/vanrides_liquid_tab_scaffold_test.dart test/features/admin/admin_shell_screen_test.dart`
- `flutter test test/l10n/app_strings_test.dart`
- `flutter analyze --no-fatal-infos --no-fatal-warnings`

## 3. Token Semantics

### Backgrounds

| Token | Meaning | Use |
| --- | --- | --- |
| `backgroundDeep` | Main app/admin dark shell | Scaffold/page background. |
| `mapBase` | Dark map environment | Map placeholders and map-adjacent views. |
| `backgroundElevated` | Elevated dark field over deep background | Secondary bands and ambient panels. |

### Surfaces

| Token | Meaning | Use |
| --- | --- | --- |
| `surface` | Main solid dark surface | Dense admin panels, forms, solid fallback. |
| `surfaceRaised` | Raised solid surface | Inputs, cards, elevated controls. |
| `surfaceGlass` | Translucent dark glass surface | Sheets/nav/overlays over map or context. |

### Borders and text

| Token | Meaning | Use |
| --- | --- | --- |
| `border` | Full-strength structural border | Strong separation. |
| `borderSubtle` | Low-strength boundary | Portal/card outlines, non-critical chrome. |
| `textPrimary` | Primary readable text | Titles and key values. |
| `textSecondary` | Secondary readable text | Descriptions and labels. |
| `textMuted` | Tertiary text | Metadata only. |

### State colors

| Token | Meaning | Use |
| --- | --- | --- |
| `stateTracking` | Movement/location/current operation | Map status, route, van tracking. |
| `stateSuccess` | Confirmed/ready | Presence confirmed, ready setup. |
| `stateWarning` | Needs attention | Pending presence, stale signal, review. |
| `stateDanger` | Error/destructive | Real failure, deletion, cancellation. |
| `stateNeutral` | Passive/secondary | Disabled or informational status. |

## 4. Layout Rules

### Mobile

- Travel/location screens are map-first.
- Bottom sheets must have clear detents: mini, medium and expanded.
- Expanded content must continue downward from the same header; it must not
  replace the top section and create a flicker.
- Primary text in chips, CTAs and sheet headers must not truncate.
- Secondary detail goes to expanded sheet or detail screen.
- Avoid horizontal scrolling for essential decisions. If there are more than
  two choices, use a compact list or segmented control.

### Portal admin

- The portal is an operational workspace, not a marketing page.
- Use dense but grouped information: next action, list, detail, secondary
  actions.
- Tables can be solid surfaces; glass is not required for dense grids.
- Entity pages should own their own access/service/guardian/driver links.
- Advanced link tooling may exist, but it should be diagnostic/secondary.

## 5. Liquid/Glass Rules

Use glass when:
- the surface sits over a map;
- the user benefits from seeing context behind the panel;
- the panel is a sheet, navigation bar, floating action group or transient
  status overlay;
- the content is short enough to remain readable.

Avoid glass when:
- the surface is a long form;
- the content is a dense admin table;
- the state is destructive or legally/operationally critical;
- the background behind the panel is too noisy for readability.

Fallback:
- High contrast, accessible navigation or disabled animations must use solid
  fallback through `VanRidesLiquidSurface.shouldUseSolidFallback`.

## 6. Motion Rules

| Motion alias | Base duration | Use |
| --- | --- | --- |
| `buttonPress` | `fast` | Press/release feedback. |
| `statusUpdate` | `standard` | Status chips, counters, banners. |
| `detentSettle` | `sheetSnap` | Bottom sheet snap points. |
| `routeRefresh` | `calmReveal` | Map/route/location refresh. |

Rules:
- Always use `VanRidesMotionPolicy.of(context)` for UI transitions that may
  affect accessibility.
- Reduced motion must set duration to zero and wobble to zero.
- Detent motion should feel springy, but not bouncy enough to hide state.
- Do not animate text changes in a way that causes layout jumps.

## 7. Component Defaults

### Buttons

- Primary: blue tracking, dark text via `onAccent`.
- Success/confirm: green action only when it confirms real participation or
  readiness.
- Warning: amber for attention, not for normal navigation.
- Danger: red only for destructive or failed states.
- Disabled: surface raised + muted text.
- Minimum comfortable height: `controlHeightComfortable`.
- Compact actions can use `controlHeightCompact`.

### Chips and status pills

- Must include visible text and semantic label when icon-only meaning would be
  ambiguous.
- Selected state cannot depend on color alone.
- Essential chips should not use ellipsis. Shorten the copy instead.

### Forms

- Use solid or raised surfaces, not heavy glass.
- First invalid field should receive the recovery focus.
- Field errors must explain the user action, not the HTTP status.
- Long forms should have visible section grouping and a persistent primary
  action.

### Sheets

- Mini state: current state and one primary meaning.
- Medium state: current state, key entity/stop and 1-3 actions.
- Expanded state: details, lists and secondary actions.
- Header must remain stable across detents.

## 8. Role-Specific Rules

### Passenger

- Prioritize: where is the van, what is my stop, do I need to decide presence.
- Map and sheet are the primary experience.
- Presence decisions should be clear and reversible when allowed.

### Driver

- Prioritize: next action, next stop, trip readiness, location permission.
- Home should not ask the driver to interpret admin concepts.
- Driver actions should be one primary CTA at a time.

### Guardian

- Prioritize: safety, location freshness, passenger status.
- Copy should not feel like passenger copy with a different name.
- Use calm status language; do not over-alert on normal waiting states.

### Admin

- Prioritize: what needs action today.
- Use entities as operational profiles.
- Keep setup and maintenance separate from historical or diagnostic data.

## 9. Implementation Checklist For Future Cards

Before touching a screen:
- identify the state from `UX-Flow-State-Matrix-2026-06-19.md`;
- choose solid vs glass;
- choose primary/secondary actions;
- define empty/error/loading states;
- verify no essential text truncates;
- verify reduced motion and high contrast behavior;
- add or update focused tests;
- include `State matrix coverage` in QA evidence.

## 10. Current Code Owners

| Area | Files |
| --- | --- |
| Global tokens | `lib/app/theme/vanrides_design_tokens.dart` |
| Motion | `lib/app/theme/vanrides_motion.dart` |
| App theme | `lib/app/theme/vanrides_theme.dart` |
| Liquid surfaces | `lib/app/widgets/vanrides_liquid_surface.dart` |
| Tab scaffold | `lib/app/widgets/vanrides_liquid_tab_scaffold.dart` |
| Admin colors | `lib/features/admin/presentation/widgets/admin_portal_colors.dart` |
| Admin theme | `lib/features/admin/presentation/widgets/admin_portal_theme.dart` |
| Tracking glass/map | `lib/features/tracking/presentation/widgets/*glass*`, `*map*` |
