# Munes changes

Running log of the work on this branch, session by session. Later sessions supersede earlier
ones where they overlap — the summary below is the state as it actually stands.

---

## Where things ended up

**New screens.** The app opens on a two-tab home: **Monitor** (the role picker) and
**Settings**. `SettingsScreen` replaces the old `CameraSettingsScreen` and covers both roles,
so a device used only for watching can finally reach its own settings.

**Settings, in full.** Camera side: lens (remembered), torch, device name, port, picture size,
frame rate, and *separate* movement and sound sensitivity sliders. Watching side: start with
sound on, listen-when-alerted (Off / Until quiet / Keep listening), movement notifications and
sound notifications as **two independent switches**, keep screen on, remembered camera. Plus
Reset all settings, behind a confirm.

**Two real bugs found and fixed, both pre-existing:**

1. **Sound alerts could never fire.** The threshold at the default sensitivity was 0.18 RMS;
   a real room measures 0.0005–0.036. Rescaled to a geometric 0.004–0.25 sweep, and the meter
   rescaled with it.
2. **Every video stream died after 15 seconds.** The Ktor CIO client ran with default engine
   settings, where `requestTimeout` is 15s — and an MJPEG response never ends. That was the
   constant disconnect/reconnect. Now `requestTimeout = 0`.

**Alert content.** One `CameraAlert(kind, magnitude)` drives every surface, with wording
derived from what was actually measured. The previous prose claimed things about the *other*
sensor ("sound was quiet") that were never measured at all.

**Television support.** Leanback launcher entry, overscan gutters, a focus ring on every
control, initial focus per screen, no auto-hiding chrome in the live view, notifications that
dismiss themselves after 12s, a fixed-height scrolling alert history, and a "Search again"
button for discovery.

**Verified on Android** (both phones, camera-to-viewer). **Not verified on iOS** — see the
final section.

---

## Session 1 — 2026-09-15

### Build and install

- Built and installed the debug APK on two phones:
  - TECNO CM7 (`140902554F006159`), Android 16
  - Huawei STK-L21 (`XMJNW20218002691`)
- Copied the APK to the Tecno's `/sdcard/Download/BabyMonitorPro-debug.apk` for sideloading
  onto the TV.
- `local.properties` was missing and the build could not find the SDK. Added it with
  `sdk.dir=/home/mbanifawaz/Android/Sdk`. The file is gitignored, so it never leaves this
  machine.
- A release install was attempted first and abandoned at your request: the `release` build
  type has no `signingConfig`, so Gradle generates no `installRelease` task and an unsigned
  APK cannot be installed. The signing change was reverted; `androidApp/build.gradle.kts` is
  untouched.

### Feature work

Four things were asked for, in this order:

1. Remote-control navigation on the TV — you could not see what was selected.
2. Settings moved out to its own tab on the main screen, holding **both** camera-mode and
   viewer-mode settings.
3. The app optimised for TV as well as phones.
4. TV notifications to dismiss themselves, and notification content reviewed so a sound
   alert and a movement alert say different, relevant things.

---

### 1. D-pad focus — you can see what you are about to press

**New:** `shared/src/commonMain/.../ui/components/Focus.kt`

- `Modifier.focusRing(...)` draws a 3dp marigold ring plus a 2% lift on the focused element.
  3dp because the app's own 1.5dp card border is invisible from a sofa; a ring rather than a
  fill because the tinted cards use their fill to say which role they are.
- `Modifier.initialFocus()` puts focus somewhere sensible when a screen opens, **on
  televisions only**. Without it the first D-pad press lands nowhere and the remote reads as
  frozen. On a phone it would pop a focus ring onto a card nobody touched, so it is a no-op
  there.

**Changed:** `ui/components/Press.kt`

- `Modifier.pressable(...)` now draws the focus ring as well as the press scale, so every
  card and row in the app became remote-navigable in one change rather than screen by screen.
- New optional `focusShape` parameter: the ring follows the surface's own corner radius.
  Call sites that use a different shape (role cards, tabs, switch rows) pass theirs.

**Changed:** `ui/layout/WindowClass.kt`

- `gutter()` returns a 48dp overscan inset on a television. TV panels crop a few percent off
  every edge, and what was being cropped was the chrome at the edges.
- New `tvOverscan()` for the vertical half of the same allowance. Zero on every other
  platform, where safe-drawing padding already handles the notch.

**New:** `isTelevision` in `core/Platform.kt` plus actuals for all four targets.

- Android reads `UiModeManager.currentModeType`, **not** `FEATURE_LEANBACK`: a phone-shaped
  APK sideloaded onto a TV box declares no leanback feature and is a television anyway —
  which is exactly how this app got onto yours.

---

### 2. Settings as a home-screen tab, covering both roles

**New:** `ui/screens/HomeScreen.kt`

- The app now opens on a two-tab home: **Monitor** (the role picker, unchanged) and
  **Settings**.
- Tabs are pills, not Material underlined tabs — an underline is two pixels of state at three
  metres, and the focus ring needs a shape to follow.
- The selected tab survives rotation but is not persisted: someone who left the app on
  Settings yesterday is opening it tonight to watch the baby.

**New:** `ui/screens/SettingsScreen.kt` — replaces `CameraSettingsScreen.kt` (deleted).

Both roles are always shown, even on a device doing neither right now. A kitchen tablet is
configured on a Sunday afternoon and used at 2am, and settings that appear only while
something is running cannot be prepared with.

*When this device is the camera* (hidden entirely on web, which cannot host):

- Front/rear lens, with the switch action when the device has two.
- Microphone state.
- Device name + rename.
- Sensitivity slider, with the live sound meter above it. The meter now only runs while this
  device is actually broadcasting — a meter frozen at zero otherwise reads as "quiet room"
  when it means "nothing is listening".

*When this device is watching* — these four had nowhere to live before, because a
viewer-only device never opens the camera screen:

- **Start with sound on** (new setting, default off). A tapped alert still overrides it: the
  app heard something, which is a different situation from opening the app to look.
- **Alert notifications** (new setting, default on). Turning it off keeps the in-app banner
  and the history; only the interruption goes.
- **Keep the screen on** (new setting, default on). Previously hardcoded on in the live view.
- **Remembered camera**, with a Forget action. Reads the `lastManualHost` that was already
  being stored but never shown.

Also: the privacy card stays, and **Stop broadcasting only appears while something is
running** — on a viewer-only device it used to be a dead control for a server that was never
there.

**Changed:** `store/AppSettings.kt` — three new persisted keys (`start_with_sound`,
`alert_notifications`, `keep_screen_awake`).

**Changed:** `App.kt` — `Routes.ROLE` now hosts `HomeScreen`; `Routes.CAMERA_SETTINGS` points
at the same `SettingsScreen`, so the camera screen's Settings button still works and shows
one screen rather than a second, different one.

**Changed:** `ui/screens/RolePickerScreen.kt` — takes a `modifier`, drops its own
`safeDrawingPadding` (the tab strip above it has already taken that inset), and scrolls, so
it fits under the tabs on a short window.

**New icon:** `BmpIcons.Sliders` for the Settings tab. Three sliders rather than a gear —
a gear at 18dp seen from three metres is a grey disc.

---

### 3. Notifications reviewed — motion and sound now say different things

This was the biggest correctness problem found, and it predates the tab work.

**The bug:** every alert built its own prose at the call site, and that prose claimed what
the *other* detector saw. Motion events were captioned `"just now · sound was quiet"` and
sound events `"just now · the picture was still"` — **always, unconditionally**. Neither was
ever measured. The camera sends motion and sound events independently and says nothing about
the other channel, so both sentences were invented. A parent deciding whether to get up was
reading a guess dressed as a reading.

**New:** `notify/Alerts.kt` — one `CameraAlert(kind, atMillis, magnitude)` used by every
surface, with `headline`, `detail` and `oneLine` derived from the numbers the camera actually
sent (`intensity` for motion, `level` for sound — the same values `PROTOCOL.md` defines).

| Kind | Headline | Detail, by measured magnitude |
|---|---|---|
| Motion | Movement in the nursery | Slight movement / Steady movement / A lot of movement |
| Sound | Sound in the nursery | Faint — a murmur or a rustle / Clearly audible / Loud — crying or a shout |

The bands sit inside the ranges the detectors can actually fire in (motion 0.005–0.20 of the
frame, sound 0.01–0.35 RMS), not across 0–1.

**Changed:** `notifyAlert(endpoint, alert)` on all four platforms — it takes the event now,
not a pre-joined sentence, because each platform has a different number of lines to fill.

- **Android:** title = which sensor fired, body = the measured detail, sub-text = the camera's
  name. Previously the title was the camera name and the body differed by a single word, so
  the shade could not tell crying from a kicked-off blanket. Separate notification ids per
  kind, so a sound alert no longer overwrites a motion one — they answer different questions.
  Category set to `CATEGORY_ALARM`.
- **iOS:** title / subtitle / body split the same three ways; the request identifier is now
  per kind for the same coalescing reason.
- **Desktop:** headline and camera in the balloon caption, detail in its body.
- **Web:** unchanged (still a no-op; the browser needs a gesture-tied permission prompt).

**Changed:** `ui/screens/LiveViewScreen.kt`

- Banner shows headline + measured detail.
- The "Earlier today" rail now carries the teddy for movement and the rattle for sound, and a
  second line with the detail. Six rows of identical rattles made six events look like one
  kind of thing.
- Notifications are gated on the new preference; the in-app banner and history are not.

---

### 4. TV-specific notification behaviour

**Changed:** `notify/Notifications.android.kt`

- On a television the alert sets `setTimeoutAfter(12s)` and takes itself away. Nobody
  dismisses a notification on a TV — there is no swipe and the remote has no gesture for it,
  so an alert left standing is still saying "the baby moved" an hour later, which is the one
  lie this app cannot afford.
- On a phone, nothing changed: it stays until tapped or swiped, because there it is the thing
  that wakes someone who is asleep.
- 12 seconds because the detectors debounce at 3, so a busy room cannot leave a permanent
  banner across the picture.

---

### TV launcher entry

**Changed:** `androidApp/src/main/AndroidManifest.xml`

- Added a `LEANBACK_LAUNCHER` intent filter. Without it the app installs on Android TV and
  then cannot be started from the home screen at all — no icon, and the only way in is a file
  manager or adb, which is what you hit.
- `android.hardware.touchscreen` and `android.software.leanback` both declared
  `required="false"`, so one APK covers phone, tablet and TV.
- `android:banner` added.

**New:** `androidApp/src/main/res/drawable/tv_banner.xml` — the 320x180 home-row banner, drawn
as a layer-list rather than shipped as a PNG so it scales to the panel's density and costs
nothing in the APK.

---

### Verification

- `./gradlew :androidApp:assembleDebug` — passes.
- `./gradlew :shared:compileKotlinJvm :shared:compileKotlinWasmJs` — passes.
- `./gradlew :shared:jvmTest` — passes.
- `./gradlew :shared:compileCommonMainKotlinMetadata` — passes.
- Installed on both phones and pushed to the Tecno's Downloads folder.

**Not verified:** the iOS changes — see "Needs a Mac" below.

**Not done yet:** the remaining screens (Find a camera, the camera screen, the live view)
inherit the focus ring through `pressable`, but their focus *order* has not been walked
through on the TV with a remote, and none of them names its own initial focus target yet.

---

## Session 2 — 2026-09-15, later

Three reports from the TV and the phones, all fixed. The last one turned out to be a real
bug that had nothing to do with the TV.

### 1. The focus ring was never actually drawn around anything

**Reported:** "padding overall for navigation highlight is not correct, it doesn't show
perfect around selected element."

**Cause:** the ring was a `Modifier.border`, and that was wrong twice over.

- **Painted underneath the card.** Modifiers draw outermost-first. Every call site wraps a
  `Surface`, and the surface paints its own cream fill from *inside* the chain — so the ring
  went down first and the card covered it, leaving only whatever overhung the edge.
- **Clipped in half.** Call sites read `.clip(shape).pressable(...)`, and a stroke centred on
  the shape's edge loses its outer half to that clip. What survived was a 1.5dp hairline —
  exactly the weight the ring exists not to be.

**Fix:** `Focus.kt` now draws the ring in `drawWithContent`, *after* the content, inset by the
full stroke width so the whole ring sits inside the bounds and no ancestor can clip it. It
follows the shape passed in, so a card, a pill and a tab each get their own radius.

### 2. The live view had no visible controls on the TV

**Reported:** "when I entered to watch a stream on TV, that screen doesn't show navigations."

**Cause:** two assumptions that only hold for touch.

- Chrome auto-hides after four seconds unless the window measures EXPANDED. TV boxes do not
  reliably report EXPANDED — plenty hand back 1920x1080 at a density that measures 640dp — so
  a 55" screen got the phone layout: bar fades, rail absent, and a remote has no tap to bring
  it back. Nothing focusable was left on screen at all.
- The whole picture is a `clickable` tap target (that tap is what reveals the chrome). Being
  clickable makes it focusable, so on a TV it was a screen-sized focus target that swallowed
  the D-pad before the rail ever got a turn.

**Fix, in `LiveViewScreen.kt`:**

- The rail and the always-on chrome are now keyed on `isTelevision` as well as the width.
- The auto-hide timer returns early on a television.
- The full-screen tap target is not installed on a television — it reveals nothing there, so
  it is pure focus theft.
- The rail's Sound button takes initial focus, with Close one press beside it.
- The "turn your phone sideways" hint no longer appears on a TV.
- Focus rings added to the two plain text buttons that survive on every layout ("Got it" on
  the alert banner, "Close" on the bottom bar), and to `MoonButton`, which sits in three
  headers and the rail.

### 3. Sound alerts never fired, and the meter never moved — a real bug

**Reported:** "what the room sounds like now doesn't move as if there is no sound", and
"movement works, sound never fires".

**Diagnosis.** Instrumented `MicController` on the Tecno and read the actual RMS off the
device rather than guessing. The mic was working perfectly — `AudioRecord` state 1, recording
state 3, 3200-byte chunks arriving steadily. The measured levels in a normal room:

```
chunk 0  read=3200 rms=0.00055
chunk 10 read=3200 rms=0.0147
chunk 20 read=3200 rms=0.0359
chunk 30 read=3200 rms=0.0086
chunk 40 read=3200 rms=0.00054
```

So the room lives between **0.0005 and 0.036 RMS**. Against that:

- **The alert threshold at the default sensitivity was 0.18.** `thresholdFor` interpolated
  linearly from 0.35 down to 0.01, so sensitivity 50 demanded 0.18 — about a shout into the
  handset. Nothing a nursery produces could ever reach it, which is why sound alerts had
  never fired for you. Motion was unaffected, which is exactly the asymmetry you saw.
- **The meter's full-scale was 0.4, linear.** A 0.036 reading is 9% of that, below the 14%
  minimum bar height, so all fourteen bars sat pinned on the floor and the meter looked
  broken rather than quiet.

**Fix:**

- `SoundDetector`: range moved to 0.004–0.25, and the sweep is now **geometric** rather than
  linear. Loudness is multiplicative — 0.004→0.02 is the same perceptual step as 0.05→0.25 —
  and a linear sweep spent ninety of its hundred stops above everything a room produces. The
  default now sits at ~0.032, just above the measured quiet floor.
- `SoundMeter`: ceiling dropped to 0.25 and the mapping square-rooted, so the height is spent
  where the readings actually are. A 0.0005 reading floors at 0.14, 0.015 lands at 0.24,
  0.036 at 0.38.
- The existing `SoundDetectorTest` cases all still pass unchanged, including the two that pin
  the behaviour at either end of the slider.

The mic diagnostics were temporary and have been reverted — `CameraController.android.kt` is
back to its committed state.

### Tested on both phones, end to end

Huawei STK-L21 as **camera**, Tecno CM7 as **viewer**, per your instruction:

- Huawei: camera role starts, `Broadcasting`, pairing address `192.168.1.32:8080`, viewer
  count live.
- Tecno: opened `bmpro://192.168.1.32:8080`, picture arrives, live view up.
- **A sound alert fired within seconds** — the thing that had never fired before:
  - In-app banner: **"Sound in the nursery" / "Faint — a murmur or a rustle"**
  - System notification, read back out of `dumpsys notification`:
    `android.title=Sound in the nursery`, `android.text=Faint — a murmur or a rustle`,
    `android.subText=HUAWEI STK-L21`, `category=alarm`, id 44 (the sound-specific id).
- Settings screen on the camera phone renders both role sections, and the meter is live.
- `./gradlew :shared:jvmTest` passes.

Motion alerts were not re-tested end to end this session — the phone was stationary — but
that path was already working and its code is unchanged apart from the shared alert model.

### Build and install

New build installed on both phones and pushed to the Tecno's
`/sdcard/Download/BabyMonitorPro-debug.apk`, overwriting the old one for the TV.

---

## Session 3 — 2026-09-15, evening

Reported from the TV and the phones: the focus ring was still cut off, the two alert kinds
shared one switch, the stream kept dropping, and the banner sat there until dismissed. Plus a
batch of new settings.

### 1. Focus ring — the remaining clipper was the lift

The ring was already being drawn after the content (session 2). What was still cutting it was
the 2% scale-up on focus: **a scaled node draws outside its own layout bounds**, and every one
of these sits inside something that clips — a `Surface` clipped to its shape, a card with
padding, a tab strip against the overscan inset. Two per cent of a full-width settings row is
several pixels past the card's edge, and the card clipped exactly the part of the ring the eye
uses.

- The lift is gone. The ring is the whole treatment now.
- It sits **6dp inside** the element (two stroke widths), so its outer edge is a full stroke
  clear of the element's own border and nothing upstream can shave it.
- It fades in over 120ms instead of snapping, so holding the D-pad down does not strobe.

### 2. Turning sound alerts off also turned movement alerts off

**Your report:** "cause when i turn sound alert off movement alerts turned off too."

Correct, and it was one switch writing one key. Now two: `motion_alert_notifications` and
`sound_alert_notifications`, each with its own row. A device that already had the old combined
key inherits its value for both — someone who turned notifications off meant "stop
interrupting me", and honouring that on both is the reading that cannot surprise them.

### 3. Listen when alerted — three states, not a switch

**Your ask:** auto-listen when an alert fires, and then either keep listening or stop again
once it is quiet.

`ListenOnAlert` is a three-way setting:

| | |
|---|---|
| **Off** | Alerts leave the sound exactly as you set it |
| **Until quiet** (default) | Sound comes on with an alert and stops once the room settles |
| **Keep listening** | Sound comes on with an alert and stays on |

- "Until quiet" waits for **30 seconds** below a 0.02 floor before muting. Deliberately
  patient: a baby settles in fits, and a speaker that cuts out after five seconds of silence
  and returns on the next snuffle is worse than one that just stays on.
- The quiet floor is *lower* than the camera's alert threshold, so a baby who has dropped to
  grizzling does not get the speaker cut mid-grizzle.
- It only ever undoes **its own** switch-on. Touching the Sound control clears the flag, and
  from then on the app will not mute you. Nothing here can silence a monitor a parent opened
  deliberately.

### 4. Alerts with the sound off — already true, now confirmed

**Your ask:** make sure alerts work when the watching device has sound off.

They already did and still do: alerts arrive on the `/control` websocket, which is opened
independently of `/audio` and is never touched by the sound toggle. Verified on the devices —
the Tecno raised both a sound alert and a movement alert while muted.

### 5. The stream kept dropping — root cause found

**Your report:** "it disconnects a lot and reconnects immediately."

**Cause:** the viewer's HTTP client is Ktor CIO, constructed with default engine settings, and
CIO's `requestTimeout` defaults to **15 seconds**. Those defaults are built for
request/response traffic. An MJPEG stream is a response that is *designed never to end*, so
every stream was being torn down fifteen seconds after it opened and reconnected two seconds
later — forever, on a completely healthy network. Nothing was ever wrong with the WiFi.

**Fix,** in both `CameraHttpClient.jvmAndroid.kt` and `CameraHttpClient.ios.kt`:

- `requestTimeout = 0` — no deadline. The stall watchdog in `MjpegFrameLoop` is what decides a
  stream is dead, and unlike a fixed deadline it can tell a stalled stream from a slow one.
- `connectTimeout = 8s` — failing to *reach* a camera should still be quick, because that is
  the case where retrying is the right answer.
- `socketTimeout = 20s` — generous rather than infinite, so a camera that vanishes mid-frame
  eventually drops instead of holding a half-read socket all night. Well past the watchdog's
  8s, so the watchdog stays the first thing to notice.

**Also:** reconnects now back off 250ms → 500 → 1s → 2s → 3s instead of a flat two seconds.
Most reconnects are a blink, and a fixed two-second wait turned each one into two seconds of
stale picture.

### 6. The banner waited to be dismissed

**Your ask:** on-screen notifications should disappear after five seconds, not only on "Got
it".

`ALERT_VISIBLE_MILLIS` was 30 seconds. Now **5**. The banner covers the top of the picture,
and on a television nobody walks over to dismiss it — a banner that waits for acknowledgement
is a banner sitting over the cot all night. The event still stays in the history rail and in
the notification shade.

### 7. New settings

All four you picked, plus the pieces above.

**Separate sensitivity sliders.** One slider used to write the same number to both detectors,
so a nursery on a noisy street could not have calm sound and alert movement — and the two
thresholds do not even measure the same kind of thing (a fraction of the frame vs. loudness).
Now two cards: *Movement alerts*, which states the threshold as a percentage of the picture,
and *Sound alerts*, which keeps the live meter because that is the evidence for that slider.

**Picture quality.** 720p / 480p / 360p and 15 / 12 / 8 fps, as pills. MJPEG sends a whole
JPEG per frame, so halving the height roughly quarters both the WiFi traffic and the encode
work on the nursery phone — the one running hot on a charger all night. A television upscales
whatever it is sent. Applied at the next broadcast start, which the card says out loud.

**Default lens and torch.** The lens choice is now remembered rather than reset to front every
night, and there is a torch switch — `Broadcaster.setTorch` existed and nothing had ever called
it. The torch row only appears while a camera is actually bound.

**Port, and reset.** The port is editable (1024–65535, only a valid one is stored) for when
something else holds 8080; the camera screen's one-tap "try 8081" only ever lasted until the
next start. *Reset all settings* removes every key rather than overwriting it — so a later
change of default still reaches anyone who reset — and sits behind a confirm, because on a
remote the focus walks past every row and a one-press reset is a row you can wipe your
settings with by leaning on the D-pad.

**Wiring that was missing:** `CameraScreen` now builds its `BroadcastConfig` from the settings
— port, capture geometry, frame rate, lens. It previously used the library defaults, so three
of these settings would have had no effect on what was actually sent.

### Tested on both phones

Huawei STK-L21 as camera, Tecno CM7 as viewer:

- Broadcasting at `192.168.1.32:8080`.
- **Picture still live 75 seconds after connecting** — past the old 15-second cliff, which is
  the direct evidence for the timeout fix.
- Movement alert fired on the muted viewer: banner read **"Movement in the nursery" / "A lot
  of movement"**, status dot marigold (live) throughout.
- Earlier in the session, a sound alert fired the same way with its own wording and its own
  notification id.
- `./gradlew :androidApp:assembleDebug :shared:compileKotlinJvm :shared:compileKotlinWasmJs
  :shared:jvmTest` — all pass.

Installed on both phones and copied to the Tecno's Downloads folder for the TV.

**Still only checkable by you, on the TV:** the focus ring now that the lift is gone, and the
live view's rail and permanent chrome.

---

## Session 4 — 2026-09-15, night

### 1. The remote went dead after an alert — found it

**Your report:** "navigation on the TV live camera view... I could navigate correctly, but
after some notifications I couldn't navigate down to turn the sound on or off."

**Cause:** a classic focus orphan. The alert banner's "Got it" is a focusable button, and the
banner now clears itself after five seconds. If the remote happened to be on that button when
the timer fired, the focused node left the composition — Compose had nowhere to move focus to,
so every subsequent D-pad press went nowhere. The banner was, in effect, a focus trap with a
five-second fuse.

**Fix, three parts:**

- `Modifier.skipDpadFocus()` — on a television the banner's action is no longer a focus target
  at all. It is pointless there anyway: the banner takes itself away, so nothing needs
  pressing. It stays focusable on every other platform, where there is no auto-dismiss.
- `rememberFocusAnchor()` / `Modifier.focusAnchor()` — the rail's Sound button is now a named
  anchor, and when an alert clears the remote is put back on it. Belt and braces: whatever
  else may ever become focusable inside a transient banner, the remote ends up somewhere
  rather than nowhere.
- The same trap existed in **Settings**: pressing "Reset" replaces the focused Reset button
  with a Cancel/Reset pair, so the node under the remote vanished. `MoveFocusWhen` now hands
  focus to **Cancel** — the safe half, so a remote that arrives there by accident is one press
  from backing out rather than one press from wiping the settings.

### 2. The alert history pushed the controls off screen

**Your ask:** make the notifications section on the live view scrollable and fixed height so
it does not push everything down.

The rail is a column, so every event added to "Earlier today" pushed Sound and Close further
down — after a busy hour they were off the bottom of a television screen entirely. The
controls disappearing *because the baby moved* is exactly backwards.

The card is now a fixed **168dp** (about four rows) that scrolls internally, so it takes the
same room whether it holds one event or twenty. The six-item display cap is gone with it —
capping only hid events that can now be scrolled to, and the list is newest-first so the cap
never bought anything at a glance.

### 3. Refresh for "On this network"

**Your ask:** add a refresh button to the on-this-network feature, optimised for TV.

- **"Search again"**, in the corner of the discovery card. It bumps a round counter that the
  browser is keyed on, so the mDNS browser is torn down and rebuilt — which is what searching
  again has to mean. mDNS answers are cached, and a browser that has already concluded the
  network is empty will go on saying so indefinitely.
- **A labelled pill, not an icon.** A 24dp circular arrow in a card corner is unreadable from
  a sofa and a poor D-pad target. This carries the word, stands 48dp tall, and takes the same
  focus ring as everything else.
- **It takes initial focus** on this screen. One press down from it is the camera list, so the
  first thing the D-pad can do is either open a camera or search again — the only two things
  the screen is for.
- Hidden entirely in a browser, which cannot search a network at all, rather than shown and
  dead.

This is the control a television needs most: a TV is switched on long after the nursery phone
was set up, so its first sweep is often the one that missed — and there is no pull-to-refresh
on a device with no touchscreen.

### Build and install

`:androidApp:assembleDebug`, `:shared:compileKotlinJvm`, `:shared:compileKotlinWasmJs` and
`:shared:jvmTest` all pass. Installed on both phones and copied to the Tecno's Downloads
folder for the TV.

**For you to check on the TV:** navigate the live view, let an alert come and go, and confirm
the D-pad still reaches Sound and Close afterwards. That is the failure this session was
about, and it is the one thing I cannot reproduce from here.

---

## Session 5 — 2026-09-15, late — the freeze

**Your report:** after alerts arrived and the sound came on, both viewers — the Tecno and the
Android TV — lagged, went unresponsive and died. Then, live: "BabyMonitor Pro isn't
responding."

### Proven, not guessed

Connected to the Tecno over wireless debugging and pulled the ANR trace off the device
(`/data/anr/anr_2026-09-15-21-11-03-541`). The main thread:

```
"main" prio=5 tid=1 Native
  syscall
  android::ClientProxy::obtainBuffer
  android::AudioTrack::obtainBuffer
  android::AudioTrack::write
  android.media.AudioTrack.write
  com.hazemafaneh.babymonitorpro.audio.AndroidAudioPlayer.write
  com.hazemafaneh.babymonitorpro.ui.screens.LiveViewScreenKt$LiveViewScreen$7$1$1$1.emit
Subject: Input dispatching timed out ... Waited 5000ms for MotionEvent
```

**The bug.** A `LaunchedEffect` body runs on the composition's dispatcher, which on Android is
the **main thread**, and `collect` runs its lambda in the *collector's* context. So every
audio chunk was calling `AudioTrack.write()` on the UI thread — and in `MODE_STREAM` that call
blocks until the track has room, roughly a tenth of a second, ten times a second, for as long
as sound was on. The RMS pass over each chunk ran there too. The UI thread was starved from
the moment the sound started: the picture juddered, input stopped being dispatched, and
Android killed the app for not answering.

**This was always broken.** Sound on a viewer has always played from the main thread. What
changed is that an alert now turns the sound on by itself, so what used to need a deliberate
tap became something the app did to itself the first time the baby made a noise — which is
why it only surfaced now, and why it hit both viewers at once.

### Fixed

- The whole audio pump now runs inside `withContext(Dispatchers.Default)`: `AudioTrack.write`,
  the RMS pass and the level bookkeeping are all off the UI thread. Compose snapshot state is
  safe to write from any thread, so the meter still updates normally.
- One `ViewerClient` for the life of the screen instead of one per toggle. "Until quiet" turns
  sound on and off by itself now, and each cycle was building and tearing down a whole Ktor
  client — engine, thread pool, sockets — for a socket that lives a minute.

### Verified

- Installed on the Tecno over wireless adb, opened the live view, tapped: **the chrome came
  back**, which a hung app cannot do. No ANR and no fatal exception in the log since install.
- Not yet exercised with audio actually flowing — the Huawei was not broadcasting at the time,
  so the live view sat on "Reconnecting". **The real test is: broadcast from the Huawei, let
  an alert turn the sound on, and leave it.**
- The TV still had the hanging build; the fixed APK is pushed to the Tecno's Downloads folder
  and needs sideloading there again.

---

## Needs a Mac — the complete iOS list

Everything below is iOS-only and **none of it has been compiled**, because iOS targets cannot
be built on Linux at all (`kotlin.native` refuses: *simulator tests require macOS*). Android,
desktop and web are built, tested and running. Common code compiles — `:shared:compileCommonMainKotlinMetadata`
passes — so the shared half of every change below is syntactically sound; what is unverified
is the three iOS source files and how the changes *behave* on a phone.

Hand this whole section to Hazem. It is written to be worked through top to bottom.

### Step 1 — build it

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

Three iOS files changed, listed below. If the first command passes, the risk drops to
behaviour only.

### Step 2 — the three changed files

**`shared/src/iosMain/.../client/CameraHttpClient.ios.kt`** — the important one.

This is the stream-stability fix, and it matters as much on iOS as on Android. The Ktor CIO
client was running with default engine settings, where `requestTimeout` is **15 seconds** —
and an MJPEG response is designed never to end, so every stream was being torn down fifteen
seconds in and reconnected. Now:

```kotlin
requestTimeout = 0                 // no deadline; the stall watchdog judges a dead stream
endpoint.connectTimeout = 8_000    // failing to *reach* a camera should still be quick
endpoint.socketTimeout = 20_000    // past the watchdog's 8s, so the watchdog notices first
endpoint.keepAliveTime = 30_000
```

The API risk: `CIOEngineConfig.endpoint` is a property, not a builder function, so these are
plain assignments rather than an `endpoint { }` block — that is how it had to be written on
the JVM side too. Same ktor artifact on both, so it should compile identically.

*Worth actually watching on a device:* open a stream and leave it for two minutes. Before the
fix it blinked every fifteen seconds.

**`shared/src/iosMain/.../notify/Notifications.ios.kt`** — the alert content rework.

- `setTitle(alert.headline)` / `setSubtitle(endpoint.name)` / `setBody(alert.detail)` — the
  three-way split that makes a sound alert and a movement alert look different on the Lock
  Screen. `setSubtitle` is the one call in the file that was not there before.
- The request identifier is now `"bmpro-alert-motion"` / `"bmpro-alert-sound"` rather than one
  shared `"bmpro-alert"`, so the two kinds stop coalescing. **Check in the simulator that two
  alerts of different kinds both appear** — that is the whole point of the change.
- The signature changed from `notifyAlert(endpoint, message: String)` to
  `notifyAlert(endpoint, alert: CameraAlert)`.

**`shared/src/iosMain/.../core/Platform.ios.kt`** — one line.

`actual val isTelevision: Boolean = false`, added only so the new `expect` compiles. tvOS is
not a target, so `false` is correct and there is nothing to test. If a tvOS target is ever
added, this is the line to revisit.

### Step 3 — what changed underneath iOS without touching iOS files

These are common-code changes that iOS inherits. They compile as common code; how they *look*
on an iPhone is unverified.

- **A Settings tab on the home screen**, replacing the old camera-only settings screen
  (`CameraSettingsScreen.kt` is deleted). Worth a look at the two-tab home on a phone-sized
  screen.
- **A pile of new settings** in `AppSettings`: separate movement/sound alert notifications,
  three-way "listen when alerted", keep-screen-awake, start-with-sound, remembered lens,
  picture size, frame rate, port, reset. All stored through `KeyValueStore`, which on iOS is
  `NSUserDefaults` — so they persist there the same way.
- **Sound detection rescaled.** The threshold range moved from 0.01–0.35 linear to 0.004–0.25
  geometric, because measurement on a real phone showed a room sits at 0.0005–0.036 RMS and
  the old default demanded 0.18 — sound alerts could never fire. The meter was rescaled to
  match. This affects the iOS camera role identically.
- **Reconnect backoff** in `MjpegFrameLoop` (hostMain, so iOS too): 250ms → 3s instead of a
  flat 2s.
- **The alert model** is now `CameraAlert(kind, atMillis, magnitude)` with wording derived
  from the measured value, replacing prose built at the call site that claimed things about
  the *other* sensor that were never measured.
- **Focus rings, initial focus and the D-pad fixes are all no-ops on iOS** — every one of them
  keys off `isTelevision`, which is `false` there. `Modifier.skipDpadFocus()` and
  `Modifier.focusAnchor()` return the receiver unchanged; `rememberFocusAnchor()` never
  requests focus. Nothing about the iPhone UI should have moved.
- **The live view's alert banner now clears itself after 5 seconds** (was 30), and the alert
  history card is a fixed-height scrolling strip. Both are common, both visible on iPhone.
- **A "Search again" button** on the Find a camera screen, which rebuilds the mDNS browser.
  On iOS that means a fresh `NSNetServiceBrowser` — worth one check that re-running discovery
  there is clean, since the iOS browser is the one implementation I have no way to exercise.

### Step 4 — the one thing to design-review

The Live Activity's viewing state now receives `alert.oneLine` — "Sound in the nursery ·
Faint — a murmur or a rustle" — where it used to receive a shorter label. The Dynamic Island
is narrow. If it truncates badly, the fix is to pass `alert.headline` there and leave the
detail to the notification; both are already on `CameraAlert`.
