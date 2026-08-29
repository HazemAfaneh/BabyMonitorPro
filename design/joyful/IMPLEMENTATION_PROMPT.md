# Paste this into Claude Code

> Working directory: your local clone of `HazemAfaneh/BabyMonitorPro` (branch `master`).
> Put `README.md` and `BabyMonitor Pro Redesign.dc.html` from this bundle somewhere in the repo
> (e.g. `design/joyful/`) first, so Claude Code can read them.

---

I'm restyling BabyMonitor Pro. The old UI is already implemented and working — this is a **visual
and copy redesign plus one feature removal**, not a rewrite of behaviour. Do not change the
networking, discovery, capture, or MJPEG code paths.

Read these two files before you start:

- `design/joyful/README.md` — the full spec: token tables, per-screen layout, motion, copy
- `design/joyful/BabyMonitor Pro Redesign.dc.html` — the HTML design reference; open it in a
  browser. Boards `3a`–`3f` are the new joyful design, `2a`/`2b` the palette exploration,
  `1a`–`1h` every state artboard. **These are design references, not code to port** — recreate
  them in the existing Compose Multiplatform / Material 3 setup using the project's own patterns.

The design system is high-fidelity: hexes, sizes, radii, durations and copy are final. Use the
exact strings.

## What changes, in the order I want it done

**1. Replace the default colour scheme with the joyful day scheme.**
Currently the app's default is a charcoal dark scheme. The new default is a warm cream scheme.
Rewrite `ui/theme/Theme.kt`'s light/default `ColorScheme` with the values in the README's
"Day scheme (joyful)" table — cream `#FFF8EF` ground, white cards, `#EADFCF` borders,
`#2A2118` / `#8A7A66` text, marigold `#F2A63B` **reserved for live and healthy only**, blueberry
`#3B4CC0` for every interactive affordance, leaf `#1F8A5F` for privacy state, berry `#D4384F` for
fault. Keep the `BmpSemantic` status layer and its `CompositionLocal` — only the hexes behind
`statusLive` / `statusDegraded` / `statusWaiting` / `statusFault` / `privacy` move.

Add the four **decorative tints** (`sky`, `lemon`, `grape`, `leaf` — hexes in the README) as a
separate `BmpTints` holder. Rule to enforce in review: a tint may never carry state. They exist
only to tell cards apart at a glance.

**2. Night mode becomes a user-controlled second `ColorScheme`.**
Delete `LocalNightDim` and every `Modifier.alpha(0.45f)` night path. Instead:
- a second `ColorScheme` (README "Night scheme"), swapped instantly — no cross-fade;
- a **44dp moon icon button** top-right of the camera and live-view headers, next to the existing
  action. Filled moon = night on;
- state is one boolean in device-local storage (Settings/DataStore), **not** in the shared
  broadcast state, so the nursery phone and the kitchen tablet don't fight over it;
- at night the four tints all collapse to `surface`, and `motion.press` / `motion.celebrate` are
  set to 0ms. The sound meter keeps animating — it's the only motion that carries information.

**3. Remove the PIN feature entirely.**
It is out of scope now. Delete, don't hide:
- the PIN toggle and PIN block on the camera/pairing screen and in camera settings;
- the PIN pad, `PIN required` and `Wrong PIN` states in the live view, and the `UNAUTHORIZED`
  UI branch (leave the protocol/server side alone if removing it is risky — just stop surfacing it);
- `· PIN required` / `· PIN needed` qualifiers on discovered-camera rows;
- the pin fields in whatever UI state holder carries them.
The pairing address (and the QR that encodes it) is now the only thing a viewer needs.

**4. Add the six baby line icons.**
Teddy, moon, footprint, rattle, shield, house — 24dp, 1.8dp stroke, all built from circles and one
path (SVG paths are in the HTML reference; convert to `ImageVector`s in a new
`ui/icons/BmpIcons.kt`). They sit in small rounded plates (11–15dp radius, tint background) and
always **beside** a label, never instead of one. Placement: teddy = app mark + camera identity +
discovered rows; moon = viewer role + the night button; footprint = pairing; rattle = sound and
alerts; shield = privacy; house = discovery.

**5. Restyle the four screens to boards 3a–3f + 2a.**
Cards get 22–24dp radii (role cards 28dp), 1.5dp `#EADFCF` borders, white or tint fills; pills
16–20dp. Per-screen layout, exact copy and every state are in the README's "Screens" section.
Highlights:
- **Role picker (2a):** teddy plate + wordmark, mint privacy line, sky-tinted Camera card,
  grape-tinted Viewer card, `LAST USED` badge in lemon.
- **Find a camera (3a):** discovery owns the top card; "Scan the code" and "Type the address" sit
  below as two small equals; found cameras are lemon-tinted rows with a teddy plate, and the row
  *is* the action.
- **Camera settings (3b):** grouped rows ≥48dp, plus a **live sound meter** — 14 bars driven by the
  RMS buffer `SoundDetector` already computes, each bar interpolating over 90ms, marigold above the
  sensitivity mark and lemon below. The sensitivity slider is set against it.
- **Live view:** picture stays the only dark surface; chrome is cream pills over it, fixed distance
  from the *screen* edges, auto-hiding after 4s.
- **Alarm (3c):** a **firm banner, never a takeover** — fixed slot below the status chip so the
  picture is never covered, `#FBE4E7` fill with a 2dp `#D4384F` outline, one 280ms 10px slide-in
  and then still (no pulse), "Got it" to dismiss, auto-clears after 30s, and a second event
  *replaces* the first rather than stacking. Copy: "Movement in the nursery" / "Sound in the
  nursery", with the other sensor's reading as metadata.
- **Tablet/desktop (3f):** ≥840dp becomes picture + a fixed 352dp cream rail (no overlay chrome),
  gutter steps 20 → 28 → 36dp, and the rail gains an "Earlier today" event strip.

**6. Motion, and only these four places.**
`motion.press` 90ms in / 140ms out, `scale(0.97)` on cards and `0.98` on wide rows — every
pressable surface. `motion.celebrate` 560ms once on pairing success (QR frame scales to 0.86 and
fades, mint disc springs from 0.66, tick strokes on, caption crossfades to the camera name).
`motion.meter` 90ms per bar, always on. `motion.banner` 280ms slide-in, no repeat. Nothing else
animates, and press + celebrate are disabled at night.

**7. Warm the copy — fact first, always.**
Contractions and second person; one warm noun per screen ("the nursery"). No exclamation marks, no
baby talk, and **no joy inside an error**: error cards stay the plainest surface in the app, and
warmth is allowed only in the recovery action ("We can use the next port instead"). Exact strings
for every state are in the README.

## Constraints

- No new dependencies, no new font. Platform default type; the pairing address keeps
  `FontFamily.Monospace`.
- Keep the M3 scale and the `Space` / shape / `BmpSemantic` token structure that's already there —
  change values and assignments, not architecture.
- Every interactive row and pill stays ≥48dp tall.
- No shadows: depth is `background` → `surface` → tint, plus the 1.5dp border.
- Don't fork screens per window size — one `WindowWidthSizeClass` check per screen root.

## When you're done

1. List the files you changed and why, grouped by the seven steps above.
2. Flag anything in the spec you couldn't implement without touching networking/capture code, and
   stop rather than refactoring those layers.
3. Show me the camera screen and the live view at 393×852 in both day and night, and the tablet
   layout at 1024×768, so I can compare against boards 3b, 3c and 3f.
