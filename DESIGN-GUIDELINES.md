# Hermes Mobile — Android & Material Design 3 Guidelines

## Purpose
This contract guarantees that Hermes Agent feels authentically native to Android and Material Design 3 (Material You), avoiding the generic, flat, or web-mimicking patterns common in AI mobile wrappers.

---

## 1. Color System & Theming (Material You / Dynamic Color)

- **Dynamic Color First:** On Android 12+ (API 31+), color palettes must dynamically derive from the system wallpaper via `dynamicLightColorScheme()` and `dynamicDarkColorScheme()`.
- **Semantic Roles Only:** Never use hardcoded hex values (`#FFFFFF`, `#1E1E1E`). Use semantic M3 color roles:
  - `colorSurface` / `colorSurfaceContainer` (low, standard, high) for layered surfaces.
  - `colorPrimary` / `colorOnPrimary` for primary CTAs and active state markers.
  - `colorSecondaryContainer` / `colorOnSecondaryContainer` for tonal chips, badges, and inactive toggles.
  - `colorOutline` / `colorOutlineVariant` for subtle structural dividers.
- **True Dark / OLED Support:** In dark mode, ensure surface contrast remains crisp without pure pitch-black washouts unless explicitly configured for battery saving.

---

## 2. Layout, Insets & Form Factors

- **Enforced Edge-to-Edge:**
  - All native views must draw behind the system bars using `WindowCompat.setDecorFitsSystemWindows(window, false)`.
  - System bars must remain transparent, never solid letterboxed black strips.
  - Consume `WindowInsetsCompat.Type.systemBars()` and `displayCutout()` to pad interactive controls appropriately.
- **Foldable & Tablet Responsiveness (Pixel 10 Pro Fold Target):**
  - **Folded (Compact, <600dp width):** Modal bottom-sheet for terminal/logs; full-screen WebView with top navigation bar.
  - **Unfolded / Expanded (>600dp width):** Persistent side-sheet or dual-pane layout allowing simultaneous chat and terminal monitoring.
  - Respect hinge position and avoid placing interactive buttons across the screen fold line.
- **Touch Targets:** Minimum 48dp × 48dp for any clickable element with at least 8dp spacing between targets.

---
## 3. Navigation & Interaction

- **Predictive Back Navigation:**
  - Support Android 14+ predictive back gestures via `OnBackPressedDispatcher` and `OnBackPressedCallback`.
  - Back gesture priority: Dismiss open sheets → Close search/filtering → Minimize to background service.
- **Haptics:**
  - Use platform haptic constants (`HapticFeedbackConstants.CONFIRM`, `GESTURE_START`, `REJECT`) for sheet snaps, action triggers, and daemon state transitions.
- **Motion & Transitions:**
  - Native sliding terminal sheet must use Material 3 standard easing (`motionEasingStandard`) and duration tokens (`motionDurationMedium2` ~300ms).

---

## 4. Typography & Accessibility

- **Type Scale:** Adhere strictly to the Material 3 typography scale (Headline, Title, Body, Label).
- **Dynamic Type / Font Scaling:** All text sizing must use `sp` units to scale gracefully up to 200% under Android accessibility settings without clipping.
- **Screen Reader Readiness (TalkBack):** Every interactive icon and button must carry a localized `contentDescription` or semantic label.

---

## 5. Native Boundary Rule

- **The WebView is strictly a content viewport.**
- All chrome — including navigation headers, sliding terminal drawers, status badges, diagnostic panels, and alert dialogs — **must be implemented as native Android Views or Jetpack Compose components**.
