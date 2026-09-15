# Handoff: BabyMonitor Pro — UI/UX redesign

## Overview
BabyMonitor Pro is a LAN-only baby monitor. One device runs as the **Camera** (broadcasts video + audio),
one or more run as **Viewers** (watch the live feed). No account, no cloud, no recording.

This redesign covers four screens — **Role picker**, **Camera (Confirm & Pair / Camera settings)**,
**Find a camera**, **Live view** — plus a night scheme, an alarm-notification moment, and
large-window layouts for tablet, bedside and desktop.

**This revision (August 2026)** restyles the whole app to a *joyful* family palette — cream ground, marigold
reserved for "live", four decorative tints, six custom baby line icons, four named motion moments — makes
night mode a **manual moon switch** rather than an automatic dim, and **removes the PIN feature entirely**.
Behaviour, networking and state machines are unchanged.

Built against `HazemAfaneh/BabyMonitorPro@master`. Every state below maps to a real value in
`BroadcastState`, `VideoStatus` or `CameraEndpoint.Source`.

## About the design files
`BabyMonitor Pro Redesign.dc.html` (+ its runtime `support.js`) is a **design reference created in HTML** —
a prototype showing intended look, copy and behaviour. It is **not production code to port**.
The task is to **recreate these designs in the target codebase's own environment** (Jetpack Compose /
Material 3 for the Android + desktop targets, or whatever the project actually uses) with its established
patterns. If no environment exists yet, pick the framework that fits the platform targets and build there.

Open the HTML in a browser to see it. It is organised as artboards:

| Board | Contents |
|---|---|
| 1a | Clickable prototype — happy path, all four screens, 393×852 |
| 1b | Camera mode — 11 states (CAM-01…CAM-11) |
| 1c | Live view — 12 states (LIVE-01…LIVE-12) |
| 1d | Find a camera — 7 states (FIND-01…FIND-07) |
| 1e | Large layouts — TAB-01 tablet camera, TAB-02 bedside viewer, DESK-01 desktop viewer, WEB-01 browser |
| 1f | Alarm moment — notification → live audio in one tap |
| 1g | Night mode — a second ColorScheme, not an alpha |
| 1h | Full token set (rendered in the joyful scheme) |
| 2a / 2b | Palette exploration — joyful everywhere vs joyful by day |
| **3a** | **Find a camera — discovery, results, pairing-success motion** |
| **3b** | **Camera settings + the moon switch, day and night, live sound meter** |
| **3c** | **The alarm moment — firm banner, both schemes** |
| **3d** | **Empty and error states** |
| **3e** | **Token set — day, night, motion** |
| **3f** | **Tablet — picture + 352dp rail, "Earlier today" strip** |

Boards **3a–3f are the current design** and supersede the styling on 1a–1h; 1a–1h remain the
authority on *which states exist* and what each one says.

## Fidelity
**High-fidelity.** Final colours, typography, spacing and interactions. Recreate the UI faithfully
using the codebase's existing Material 3 setup. Copy is final — use the exact strings.

---

## Design tokens

### Day scheme (joyful) — the default. M3 `ColorScheme` roles
| Role | Hex | Use |
|---|---|---|
| background | #FFF8EF | Cream ground, every day screen |
| surface | #FFFFFF | Cards |
| surfaceVariant | #F4F1EA | Rows, disabled plates, keypad ghost keys |
| onSurface | #2A2118 | Primary text — 12.4:1 on cream |
| onSurfaceVariant | #8A7A66 | Labels, metadata, timestamps |
| outline | #DFD3C2 | Field borders, unselected controls |
| outlineVariant | #EADFCF | Card borders — carries all structure |
| primary | #F2A63B | **RESERVED: live and healthy only** |
| onPrimary | #3D2A05 | Text and icons on marigold fills |
| primaryBright | #FFD84D | Joy accent — app mark, meter body. **Never a state** |
| secondary | #3B4CC0 | Every interactive affordance — 7.1:1 on cream |
| tertiary | #1F8A5F | Privacy state only |
| error | #D4384F | Fault, Stop broadcasting |
| errorContainer | #FBE4E7 | Error card fill — the outline does the shouting |
| onErrorContainer | #8E2436 | Error text — 6.8:1 on the fill |

### Decorative tints — `BmpTints`, never carry state
| Token | Fill | Border | Use |
|---|---|---|---|
| tint.sky | #EAF4FB | #C3DFF1 | Camera role, QR and browser cards |
| tint.lemon | #FFF3CE | #F2DFA3 | Sound, discovered rows, event strip |
| tint.grape | #F3EBFC | #DFCDF4 | Viewer role, moon surfaces |
| tint.leaf | #E6F5EC | #C4E6D3 | Privacy card, paired disc |

A tint tells cards apart at a glance; a parent never has to learn them. At night all four resolve to
`surface`. Bezel colour in the boards (#E8DCC9) is mock chrome, not a UI token.

### Semantic layer — `BmpSemantic`, exposed via a `CompositionLocal`
Do **not** let call sites pick `primary` for status. Bind state → colour once, here:

| Alias | → role | Bound to |
|---|---|---|
| statusLive | primary #F2A63B | `VideoStatus.LIVE` / broadcaster running |
| statusDegraded | secondary #3B4CC0 | test pattern, sound-only, no video yet |
| statusWaiting | onSurfaceVariant #8A7A66 | connecting, starting, reconnecting |
| statusFault | error #D4384F | port taken, address refused, camera stopped |
| privacy | tertiary #1F8A5F | the "on your WiFi" line, everywhere |

The status dot is a **state enum, not a boolean** — a future `recording` state costs one token and no layout.

### Night scheme (same roles, second `ColorScheme` — NOT `Modifier.alpha`)
| Role | Hex | Note |
|---|---|---|
| background | #07080C | −62% luminance vs day |
| surface | #0C0E14 | All four tints collapse here |
| surfaceVariant | #14161F | Icon plates, rows |
| onSurface | #B9BECE | 7.3:1 on the new ground |
| onSurfaceVariant | #7B8092 | 4.6:1 — labels only, never a value |
| outlineVariant | #23262F | The only structure at night |
| primary | #C9924A | Marigold, chroma pulled back |
| primaryDim | #6E5228 | Meter body below the sensitivity mark |
| secondary | #7E88BE | Interactive, dimmed |
| tertiary | #5E9A85 | Privacy, dimmed |
| error | #B06B62 | Fault. Dimmed, never brightened for urgency |

Rationale: `Modifier.alpha(0.45f)` fades emitted light *and* legibility by the same factor. Dropping the
surfaces toward black while keeping text contrast **above 7:1** emits less light and reads better.

### The moon switch — how night engages
Manual, and manual only: a **44dp moon icon button** top-right of the camera and live-view headers,
beside the existing action. Filled moon = night on. It writes one boolean to **device-local** storage,
not to the shared broadcast state, so two devices never fight over it. The swap is instant (0ms) — a
colour cross-fade in a dark room is itself motion. A parent already holding the phone in a dark room is
the only reliable sensor, which is why there is no sunset schedule and no light-sensor trigger.

What night keeps: the six icons, every shape, every radius, all the layout, and the sound meter's motion.
What it drops: the four tints, the marigold fills, press feedback and the pairing celebration.

### Motion tokens — four places, nothing else
| Token | Spec | Use |
|---|---|---|
| motion.press | 90ms in / 140ms out | `scale(0.97)` on cards, `0.98` on wide rows. Every pressable surface |
| motion.celebrate | 560ms, once | Pairing success: QR frame → 0.86 + fade (140ms), mint disc springs from 0.66 (240ms), tick strokes on (180ms), caption crossfades to the camera name |
| motion.meter | 90ms per bar | Sound meter interpolation. Always on, in both schemes |
| motion.banner | 280ms | Alert banner slides 10px down, once, then still. No pulse, no repeat |
| motion.scheme | 0ms | Moon toggle is instant, not a fade |

`press` and `celebrate` are 0ms when the moon is on.

### Typography — M3 scale, unmodified. Roles assigned:
| Role | Size | Use |
|---|---|---|
| headlineMedium | 28sp | Role picker title only |
| titleLarge | 22sp | Role card titles, screen headers |
| titleMedium | 16sp | Camera name in live-view bar, discovered rows |
| bodyLarge | 16sp | Settings row labels, advisory text |
| bodyMedium | 14sp | Descriptions, empty-state prose |
| bodySmall | 12sp | Metadata, helper text, the privacy line |
| labelLarge | 14sp | Status chips, buttons, section headers (all-caps, letterSpacing .06em) |
| labelMedium | 12sp | Latency figure, timestamps |

**One override only:** the pairing address takes `FontFamily.Monospace` — it gets read aloud across a room,
so it needs tabular, unambiguous figures.
No new font dependency — platform default everywhere else. (If a brand face is ever wanted: IBM Plex Sans +
IBM Plex Mono, fallback `FontFamily.Default`.)

### Spacing scale — `Space` object, this is the whole padding system
| Token | Value | Use |
|---|---|---|
| Space.xxs | 4dp | Label → value |
| Space.xs | 8dp | Inside a row |
| Space.sm | 12dp | Between cards |
| Space.md | 16dp | Card internals |
| Space.lg | 20dp | **Screen gutter (phone)** |
| Space.xl | 24dp | Card padding, role cards |
| Space.xxl | 36dp | **Screen gutter (tablet / desktop)** |
| Touch.min | 48dp | Minimum height of every interactive row and pill |

Padding rules, stated explicitly so nothing is guessed:
- **Phone screen gutter: 20dp left/right.** Content never touches the edge.
- **Card padding: 24dp** all round for role cards and the pairing card; **20dp 24dp** for compact rows.
- **Card-to-card gap: 12–16dp** (`Space.sm`/`Space.md`), never per-child margins — use `Arrangement.spacedBy`.
- **Bottom safe area:** the live view's floating bar sits 24dp above the bottom inset; the camera screen's
  scroll content ends with 24dp bottom padding.
- **Status bar:** 50dp reserved at the top of every phone screen; header row then gets 6dp top / 12dp bottom.
- **Preview frame insets:** overlay chips sit 12dp from the preview's own edges on phone, 16dp on tablet.
- Every tappable row is at least 48dp tall even when its content is shorter — pad, don't stretch text.

### Shape — radii unchanged from `BmpShapes`; the assignment changed
| Token | Value | Assigned to |
|---|---|---|
| extraSmall | 8dp | chips' inner pills, badges |
| small | 12dp | small controls |
| medium | 16dp | rows inside cards |
| large | 22–24dp | every content card, preview frames |
| extraLarge | 28dp | live view's floating bar, role cards |

Icon plates (the tinted square behind each baby icon) are 11–15dp radius, 28–46dp square.

### Elevation
**No shadows anywhere.** Depth is `background` → `surface` → tint, plus a **1.5dp `outlineVariant` border**
on every card (1dp at night, where the border is the only structure). No `tonalElevation`.

### Icons — six custom baby line icons + the functional Material set
**Baby set** (new `ui/icons/BmpIcons.kt`, 24dp, 1.8dp stroke, circles and one path each — SVG paths are in
the HTML reference; they survive at 20dp):

| Icon | Means | Appears on |
|---|---|---|
| Teddy | the app, this camera | app mark, camera identity, discovered rows, live-view bar |
| Moon | viewer, night | Viewer role card, the night button |
| Footprint | pairing | "Pair another device" card |
| Rattle | sound, alerts | microphone row, sound meter, alert banner |
| Shield | privacy | every "on your WiFi" line |
| House | discovery | Find-a-camera empty state |

They sit in a tinted plate and always **beside** a label, never instead of one: a half-asleep parent reads
the word, and the icon is what makes the screen feel like it was made for their kid. Never on an error.

**Functional Material icons, unchanged:** `Close`, `ContentCopy`, `ChevronRight`,
`VolumeUp`/`VolumeOff`, `QrCodeScanner`, `Settings`, plus a backspace glyph on the numeric field.

---

## Screens

### 1. Role picker
**Purpose:** choose Camera or Viewer, once per session.
**Layout:** full-bleed, vertically centred, 24dp gutter, 34dp bottom padding, 16dp gaps.
- Title `BabyMonitor\nPro` — headlineMedium 27sp/1.15, letterSpacing −.01em, onSurface.
- Teddy plate: 52dp square, radius 18dp, `primaryBright` #FFD84D fill, #5A4415 icon, beside the wordmark.
- Privacy line: shield icon + "Everything stays on your WiFi", bodySmall 13sp, tertiary #1F8A5F.
- **Camera card:** `tint.sky` #EAF4FB, 1.5dp #C3DFF1 border, radius 28dp, padding 22–24dp, white icon
  plate with the camera glyph.
  Title "Use this device\nas Camera" titleLarge 21sp; description "Stays in the nursery. Broadcasts video
  and sound to your other devices." bodySmall 13sp onSurfaceVariant, 8dp below title.
- **Viewer card:** same geometry, `tint.grape` #F3EBFC, 1.5dp #DFCDF4 border, white plate + moon icon.
  Title "Watch a camera"; description "Find the nursery
  camera on this network and open the live view."
- A `LAST USED` badge (#FFD84D fill, #5A4415 text, 10sp bold, 4×8dp padding, radius 7dp)
  sits top-right of whichever card was last chosen.
- Footer, bodySmall 12sp onSurfaceVariant: "No account, no cloud, no recording. Video and audio travel
  directly between your devices." — the only place the privacy promise is prose.

### 2. Camera — **split into two destinations**
The single scrolling screen becomes **Confirm & Pair** (the whole screen for the first two minutes) plus
**Camera settings** (a second destination reached from a header action). Reason: the urgent one-time job
should not share vertical space with nine controls a parent touches once ever — today that pushes the
privacy note and the stop action below the fold on a 393pt phone.

**Header:** device name titleLarge 20sp + a text action ("Settings" / "Confirm & Pair") in secondary
#9AA6E8, 48dp min height.

**Confirm & Pair body** (20dp gutter, 14dp gaps): self-preview frame → status chip + viewer pill overlay →
advisory/banner slot → pairing card (address + QR) → privacy line → Stop broadcasting.

- **Preview frame:** radius 22dp, dark picture (the only dark surface by day), 1.5dp #EADFCF border, takes
  the *frame's* aspect
  (9:16 portrait 344dp tall, 16:9 landscape 210dp tall) — never cropped or letterboxed; the card below
  simply moves up.
- **Status chip and viewer pill share one flex row** inset 12dp from the preview's edges: they shrink and
  wrap rather than colliding. Chip = 8dp status dot + label on a **cream #FFF8EF pill**, 7×12dp padding,
  radius 20dp — cream chrome over the picture is the joyful treatment, day and night differ only in the pill.
- **Pairing card:** footprint icon + "PAIR ANOTHER DEVICE" label; address in monospace 23–26sp with the
  `:8080` port in onSurfaceVariant; "Copy" action in
  secondary; 1dp divider; QR (9×9 stand-in here — real code from `QrEncoder`, payload `bmpro://`) on a
  #F4F1EA plate, radius 14dp.
- **Privacy line:** "On your WiFi only · nothing uploaded", tertiary, same slot on every screen.
- **Stop broadcasting:** outlined, 1.5dp error #D4384F, radius 18dp, min height 54–56dp, error label.

#### Camera states (board 1b)
| ID | State | Chip | Dot | Notes |
|---|---|---|---|---|
| CAM-01 | Waiting for permission | "Waiting for permission" | waiting | Address reads "Waiting for the network…", QR pending. Draw the card as explicitly pending — today the broadcaster starts twice around the dialog and the second start tears down the address the first drew. |
| CAM-02 | Camera denied — sound only | "Sound only" | degraded | 16:9 210dp **audio level meter** instead of a black rectangle. Advisory: "Camera access is off. Sound is still going out. Turn video back on in Settings." |
| CAM-03 | No camera hardware | "Test pattern" | degraded | Meta "SYNTHETIC · 1280×720 · 12 fps". Advisory: "This device has no working camera. Viewers get a test pattern, so sound and pairing can still be checked." |
| CAM-04 | Port already taken | "Stopped" | fault | Error banner **with an action**: "Port 8080 is already in use on this device." → "Broadcast on 8081 instead". Address "No address yet". |
| CAM-05 | No WiFi address yet | "Starting" | waiting | Capturing but not reachable. **Withhold the address until it is routable** — `isPrivateIpv4` currently accepts 169.254 self-assigned addresses, and every viewer that types one gets refused. |
| CAM-06 | Starting up | "Starting" | waiting | "FIRST FRAME PENDING". No spinner — the old judder was the double start, not slow capture. |
| CAM-07 | Broadcasting, nobody watching | "Broadcasting" | live | Viewer pill prints **"Nobody watching"** — zero is a fact, and today the pill vanishing looks like a broken count. |
| CAM-08 | Broadcasting, 1 viewer | "Broadcasting" | live | The optimised state. |
| CAM-09 | 3 viewers, landscape frame | "Broadcasting" | live | 16:9, 210dp. |
| CAM-11 | Night dim, settled | "Broadcasting" | night amber #C9924A | Whole board on the night scheme. |

**Camera settings** (second destination): grouped rows, each ≥48dp, labels bodyLarge, values/controls in
secondary. Sensitivity slider label reads Off / Low / Medium / High (0 / <34 / <67 / ≥67).

### 3. Find a camera — three routes, ranked
Discovery owns the top card; QR and manual sit below as two small equals. Today all three are stacked with
equal weight, so the fallback competes with the good path.

| ID | State | Notes |
|---|---|---|
| FIND-01 | Searching, nothing found | Empty state "Still looking" + "Open BabyMonitor Pro on the nursery device and choose 'Use this device as Camera'." |
| FIND-02 | One camera found | The **row is the action** — no Connect button, no address to read. Lemon-tinted row, teddy plate, "Nursery Pixel 8 / 192.168.1.42:8080". Copy: "Still looking around" while searching. |
| FIND-03 | Several cameras | Row carries the reason to choose: device name + address + qualifier ("· test pattern"). |
| FIND-04 | Web — cannot search | "This browser cannot search your network" / "Browsers have no way to look for devices on your WiFi. Type the address the camera screen shows." Manual entry is **promoted to primary** here. Footer: "Nothing about this connection leaves your network — the browser talks straight to the camera." |
| FIND-05 | Malformed address | Field border error. "That is not a full address. It should look like 192.168.1.42, or 192.168.1.42:8080 if the port was changed." Connect is **disabled**, not failing silently. |
| FIND-06 | QR scanner | Hint "Point at the code on the camera screen". Reached from a labelled route card, not a bare text button. |
| FIND-07 | QR scanner, permission denied | "Scanning is unavailable" — the dead end must offer the manual route. |

Rows are a vertical stack of endpoint rows. **Seam left open:** a multi-camera grid later swaps the row for
a tile with no restructure.

### 4. Live view
Full-bleed picture; floating bottom bar (radius 28dp, `surface`) with camera name, sound pill and Close.
Chrome auto-hides after **4s** (tweakable 2–10s) and returns on tap. Chrome keeps a fixed distance from the
**screen** edges, not the picture edges, so portrait and landscape share one layout and neither crops.

| ID | State | Chip | Dot | Notes |
|---|---|---|---|---|
| LIVE-01 | Connecting | "Connecting" | waiting | Prints the destination: "Reaching 192.168.1.42:8080 on this network." |
| LIVE-02 | Live | "Live" | live | Latency "42 ms" in labelMedium. Amber now means only this. |
| LIVE-03 | Reconnecting (phone roamed) | "Reconnecting" | waiting | **Last frame held at 30% opacity** — context without claiming currency. No blink. Reason: "The nursery device moved to another access point. Retrying every 2 seconds." |
| LIVE-04 | Connected, no video arriving | "Connected · no picture" | fault | **New `VideoStatus` case.** Today this shows LIVE over black — the most dangerous state in the app. "No frame for 8 seconds." + "The camera is answering on the control channel but sending no frames. Sound is unaffected." |
| LIVE-07 | Camera closed the app | "Camera stopped" | fault | "The nursery device closed BabyMonitor Pro." + "Nothing is being sent. This view reconnects on its own once the app is opened there again." Do **not** say "No connection" — that sends the parent to check WiFi. |
| LIVE-08 | Movement detected | "Live" | live | Alert banner in a **fixed slot**, outlined, out of the amber family so the alert and the healthy state are not the same colour. |
| LIVE-09 | Sound detected | "Live" | live | Same slot, meta "3s ago". A second event **replaces** the first and re-times it — stacking would push the picture out of view. |
| LIVE-10 | Sound on | "Live" | live | **Filled = on, outline = off.** "Sound off" as a TextButton read as an instruction, not a state. |
| LIVE-11 | Audio unavailable | "Live" | live | Pill reads "No sound", disabled styling **plus** a reason: "This camera has no microphone available, so there is nothing to listen to." |
| LIVE-12 | Live, landscape frame | "Live" | live | 16:9. |

**Seam left open:** the bottom bar has one free slot left of Sound — that is where talk-back goes.

### 5. The alarm moment (boards 3c + 1f)
**Firm, never a takeover.** The banner sits in a fixed slot 112dp from the top — below the status chip, in
dead space above the picture — so the frame is never covered: the alert and the evidence are visible in one
glance. `errorContainer` #FBE4E7 fill, **2dp #D4384F outline** (a full red card at 3am is a light source),
rattle icon in a plate, title + metadata, and a "Got it" action in secondary. Night: #180F11 fill, 1.5dp
#6B3038 outline, #D8B4AE title.

- One 280ms 10px slide-in, then **still** — no pulse, no repeat. A pulsing alert in a dark room makes a
  parent's own heart rate the thing they notice.
- "Got it" dismisses. Unacknowledged it clears after **30s**, so a stale alert can't be read as a live one.
- A second event **replaces** the first and re-times it. Stacking two banners would push the picture off
  screen — the exact failure this choice avoids.
- Copy: "Movement in the nursery" / "Sound in the nursery" (it says *where*, which matters as soon as a
  second camera exists), with the other sensor's reading as metadata: "just now · sound was quiet".

Notification → live audio in **one tap** still requires a code change: `notifyAlert` gains an `autoAudio`
flag so the notification action opens the live view with sound already unmuted, instead of landing muted.
The lock-screen notification is a cream card with the same copy.

### 5b. The sound meter (board 3b)
14 bars, driven by the RMS buffer `SoundDetector` **already computes** — no new capture path. Each bar
interpolates over 90ms toward its target so a quiet room breathes rather than flickers. Bars above the
sensitivity mark take `primary` #F2A63B, below it `primaryBright` #FFD84D (night: #C9924A / #6E5228).
It sits directly above the sensitivity slider so the parent sets the threshold against the actual room, and
it is the only element that animates in both schemes.

---

## Multiple screen sizes

One `WindowWidthSizeClass` check at each screen root. **Same composables, only the container changes** —
do not fork the screens.

| Class | Width | Layout |
|---|---|---|
| Compact | < 600dp | Phone. Single column, 20dp gutter. Camera screen = the two destinations described above. |
| Medium | 600–839dp | Phone layout at a wider gutter (28dp), max content width 560dp centred. |
| Expanded | ≥ 840dp | Two columns, 36dp gutter. The live view's floating bar becomes a fixed **352dp cream rail** beside the picture (nothing ever overlaps the frame), holding camera identity, the live sound meter, an **"Earlier today"** two-line event strip, the sound button and the moon. Same components, same order, unstacked; type sizes unchanged. Desktop is this with a 400dp rail. |

### TAB-01 — tablet as the camera, landscape (1280×800)
- Header: 26dp top / 20dp bottom, 36dp side padding. Device name 24sp + inline mint privacy pill + "Settings".
- Body: `grid-template-columns: 1fr 420px`, **24dp gap**, 36dp side + bottom padding, neither column scrolls.
- Left = preview, radius 20dp, overlay chips inset 16dp.
- Right rail = pairing card (address monospace 26sp, **150dp QR** — big enough to scan from across the room,
  which is the whole point of the QR route), then Stop broadcasting pinned to the bottom (`margin-top:auto`).
- Landscape is where the split earns its keep: the two jobs become two columns.

### TAB-02 — bedside viewer, night, chrome hidden (1280×800)
- Black ground. Picture centred at its native 9:16 aspect, full height, letterbox left/right left black.
- Chrome (when shown) inset 28dp, on `rgba(7,8,12,.7)` pills, night scheme colours.
- One tap from anything brings chrome back.

### DESK-01 — desktop viewer (1280×800)
- 44dp title bar, `surface` #171925, 1dp #2A2E3E bottom border, 16dp side padding; window title
  "Nursery Pixel 8 — BabyMonitor Pro".
- Body: `grid-template-columns: 1fr 320px`. Left = picture on black at native aspect. Right = a
  persistent rail (status, latency, sound, in-session alert list) instead of auto-hiding chrome — a desktop
  has the room and the pointer never "taps to reveal".

### WEB-01 — browser band (1440×900)
- 38dp browser chrome band, then the DESK-01 layout. Discovery is unavailable (FIND-04); manual entry is
  the primary route.

**Responsive rules that apply everywhere:** the preview container takes the incoming frame's aspect ratio
(never a fixed box); overlay chrome is positioned against the screen edges; interactive rows keep 48dp
minimum height at every size; the gutter steps 20 → 28 → 36dp across the three width classes.

---

## Interactions & behaviour
- **Role picker → Camera / Viewer.** Last-used role gets the `LAST USED` badge; it does not auto-start.
- **Camera header action** toggles Confirm & Pair ↔ Camera settings (no back stack surprise).
- **Copy address** → snackbar-less inline confirmation ("Copied"), 1.5s.
- **Find a camera** row tap → live view, connecting immediately. Manual Connect is disabled until the
  address parses as `ipv4` or `ipv4:port`.
- **Live view chrome:** visible on entry, auto-hides after 4s (2–10s configurable), any tap re-shows and
  re-arms the timer.
- **Alerts:** a new alert replaces the current one and restarts its dwell timer. Fixed slot, never stacked.
- **Reconnect:** retry every 2s; picture held at 30% opacity throughout; no flashing.
- **Press feedback:** every pressable surface scales to 0.97 (rows 0.98) over 90ms and releases over 140ms.
- **Pairing success:** the 560ms celebrate sequence, then the live view pushes in.
- **Night:** the moon button swaps the scheme instantly and disables press + celebrate.
- **Transitions:** 200ms standard easing for chrome fade; 280ms for the banner. Nothing else animates —
  motion in a dark bedroom is noise.

## State management
- `screen`: role | camera | find | live
- `camTab`: confirm | settings
- `lastRole`: camera | viewer (persisted)
- `viewers`: Int (0 is printed, not hidden)
- `sensitivity`: Int 0–100 → Off / Low / Medium / High
- `night`: Boolean, **device-local persisted**, set only by the moon button → swaps `ColorScheme`, never alpha
- `soundLevel`: FloatArray(14) from the existing RMS buffer → the meter
- `sound`: Boolean, `chromeVisible`: Boolean + timer, `alert`: Alert? + timer
- Broadcast/video status enums drive `BmpSemantic.status*` — one binding, not per-call-site colour choices.
- Data: mDNS/NSD discovery for the endpoint list; `isPrivateIpv4` must reject 169.254/16 before an address
  is shown as pairable.

## Assets
None to import. Six Material core icons (listed above), no custom artwork, no bundled font. The QR matrix in
the mock is a 9×9 stand-in — the real code comes from the existing `QrEncoder`.

## Files in this bundle
- `IMPLEMENTATION_PROMPT.md` — **paste this into Claude Code to start the work**
- `BabyMonitor Pro Redesign.dc.html` — all artboards and the clickable prototype (open in a browser)
- `support.js` — runtime the HTML needs; no design content

## Not designed, deliberately
Multi-camera grid (the endpoint row swaps for a tile, no restructure) · talk-back (the free slot left of
Sound in the live bar) · a `recording` status (one more enum case and token, no layout change) ·
the alert-history screen the tablet's "Earlier today" strip implies · a joyful treatment for the
Android heads-up notification itself · **the PIN feature, removed on purpose in this revision**.
