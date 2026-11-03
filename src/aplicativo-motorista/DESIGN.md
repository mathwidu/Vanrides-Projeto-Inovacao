# Design

## Overview

VanRides uses a dark premium operational interface for mobile trip experiences. The design should feel close to a high-trust mobility product, with Uber as the main reference, but adapted to planned van operations with multiple stops, guardians and company-managed service days.

The map is the primary stage. Sheets, markers, route lines and state labels exist to make the map understandable, not to cover it.

## Theme

The default mobile trip theme is dark, premium and useful. It should feel like a calm night-mode mobility app, not a vehicle cockpit.

Use dark surfaces for map-first experiences:

- Map background: dark, low-glare and visually quiet.
- Primary sheet: elevated charcoal, not pure black.
- Secondary sheet: slightly lighter charcoal for nested operational detail.
- Borders: subtle cool gray with enough contrast for night use.
- Text: warm off-white for primary content, muted blue-gray for secondary content.

Admin web surfaces may remain lighter and denser when task efficiency matters, but live mobile driver, passenger and guardian flows should prefer the dark premium map system.

## Color Palette

Use OKLCH tokens as the source of truth.

```text
--vr-bg-deep: oklch(16% 0.018 250);
--vr-bg-map: oklch(19% 0.022 245);
--vr-surface: oklch(23% 0.018 248);
--vr-surface-raised: oklch(28% 0.02 248);
--vr-border: oklch(39% 0.025 248);

--vr-text-primary: oklch(94% 0.012 250);
--vr-text-secondary: oklch(75% 0.026 248);
--vr-text-muted: oklch(62% 0.026 248);

--vr-green-action: oklch(72% 0.17 152);
--vr-green-action-strong: oklch(64% 0.18 152);
--vr-blue-tracking: oklch(71% 0.16 232);
--vr-blue-tracking-strong: oklch(63% 0.17 232);
--vr-amber-warning: oklch(78% 0.15 78);
--vr-red-danger: oklch(64% 0.19 28);
```

Color roles:

- Green means operation, confirmation, active route and primary driver actions.
- Blue means live location, tracking, route focus, ETA and signal.
- Amber means attention, delay or partial confidence.
- Red means real error or destructive state.

Do not use color as the only signal. Pair state color with text, icon shape, position or pattern.

## Logo Direction

The logo should include a van or small bus element. It should still feel premium, not childish.

Current concept reference:

- `docs/design/branding/vanrides-logo-concept.svg`
- `docs/design/branding/vanrides-map-marker-concepts.svg`

Preferred direction:

- Wordmark: `VanRides` in a clean, confident sans.
- Symbol: a simplified van or bus silhouette combined with route motion.
- Avoid school-bus clip art, cartoon faces, heavy outlines or overly literal road signs.
- The app icon may use the van/bus more explicitly than the wordmark.
- Map markers can be more expressive than the logo: a recognizable van marker for the driver and a person/passenger marker for the rider.

## Typography

Use system UI fonts by default:

```text
-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif
```

Hierarchy:

- Screen title: 22 to 24 px, semibold.
- Sheet title: 18 to 20 px, semibold.
- Primary metric: 28 to 34 px, semibold, only when it is the key trip fact.
- Body: 15 to 16 px.
- Caption/status: 12 to 13 px.

Avoid display fonts and oversized marketing typography in operational flows. The interface should read quickly on a moving phone, in a car, outdoors or in low light.

## Map System

The map must not feel like Google Maps pasted into the app.

Core requirements:

- Use a restrained dark map style.
- Keep the van marker larger and more recognizable than generic pins.
- Use a person/passenger marker instead of a generic passenger pin.
- Use stop markers that show route order when the order matters.
- Use route lines with two layers: full planned route and active remaining route.
- Use a clear visual distinction between completed, skipped, next and future stops.
- Prefer smooth marker interpolation when fresh location updates arrive.
- Show stale location honestly with a visible "last updated" or "signal paused" state.

The map should answer three questions before the sheet does:

1. Where is the van?
2. Where am I or my passenger?
3. What remains before the van reaches me?

## Marker System

Van marker:

- Custom van or small bus silhouette.
- Blue tracking glow when live.
- Amber pulse when stale or delayed.
- Direction-aware rotation when heading is available.

Passenger marker:

- Person/avatar-like symbol, not a pin.
- Should be calm and readable at small sizes.
- Guardian view may highlight the selected passenger with a ring.

Stop markers:

- Small numbered dots or compact stop chips.
- Next stop gets strongest emphasis.
- Completed stops become subdued.
- Skipped/no-show stops use a non-color-only state.

## Layout Strategy

Mobile live surfaces use a map-first hierarchy:

1. Full-screen map.
2. Compact top status strip.
3. Bottom sheet with the next useful fact.
4. Secondary detail drawer only when needed.

Avoid dashboard grids in live trip surfaces. A user should not have to scan multiple cards to understand the trip.

Passenger and guardian primary sheet:

- ETA or status.
- Stops remaining.
- Passenger position in the queue.
- Van signal freshness.
- One primary action when needed.

Driver primary sheet:

- Next stop or next passenger.
- Primary action for the current state.
- Remaining stops.
- Route progress.
- Secondary actions behind a compact affordance.

## Motion

Motion should convey state, not decoration.

Allowed:

- Smooth van marker movement between GPS updates.
- Camera easing when centering on the van, passenger or full route.
- Bottom sheet snap transitions.
- Subtle live signal pulse.
- Route progress update animation.

Avoid:

- Busy cockpit animations.
- Repeating decorative pulses across many markers.
- Page-load choreography.
- Motion required for comprehension.

Use reduced-motion alternatives for marker interpolation, pulses and sheet transitions.

## Components

Core components for the premium map phase:

- `VanRidesMapShell`
- `VanRidesMapTopStatus`
- `VanRidesLiveVehicleMarker`
- `VanRidesPassengerMarker`
- `VanRidesStopMarker`
- `VanRidesRouteProgressLine`
- `VanRidesTripBottomSheet`
- `VanRidesSignalState`
- `VanRidesStopsRemainingSummary`

Every component should define default, loading, stale, empty, error and reduced-motion states when applicable.

## Content Voice

Copy should be direct and operational:

- "Van a 4 paradas"
- "Chega em cerca de 8 min"
- "Sinal ao vivo"
- "Ultima atualizacao ha 2 min"
- "Proxima parada: Ana"
- "Aguardando inicio da viagem"

Avoid technical backend language such as snapshot, rotaVersao, WebSocket, polling or fixture.

## Accessibility

Target WCAG AA.

Requirements:

- Strong contrast between text and dark sheets.
- Marker meaning must not depend on color only.
- Touch targets should be at least 44 px where feasible.
- Dynamic text should not break bottom sheets.
- Reduced-motion mode should preserve comprehension.
- Screen reader labels should describe van location, selected passenger, ETA and stop count in human language.
