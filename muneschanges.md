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

## Session 6 — 2026-09-16 — the backlog

Eleven items from WhatsApp. Working through all of them; this section grows as each lands.

### Done: a port nothing else wants

`Bmp.DEFAULT_PORT` is now **47821**, not 8080. 8080 is the most contested port on a home
network — a dev server, a router's admin page, a media server, a second copy of this app —
and a camera that cannot bind it prints no pairing address at all. 47821 is in the registered
range and assigned to nothing. Safe to change because both ends are this app: the QR code and
the pairing line both carry the port, and the port setting still overrides it. `PROTOCOL.md`,
the README and the two tests that assumed the default were updated with it.

### Done: the camera stops being discoverable when its phone locks

**Cause:** with the screen off, Android puts the WiFi chip into power save, where it wakes
periodically and **drops most multicast**. mDNS is entirely multicast, so the camera stopped
answering discovery queries the moment the phone locked — it was broadcasting perfectly well,
it just could not be found. The same power save is what made streams stutter shortly after the
screen went dark.

**Fix:** `BroadcastService` now holds two locks for as long as it runs:

- a **WiFi lock** (`WIFI_MODE_FULL_LOW_LATENCY`, `FULL_HIGH_PERF` below API 29) to keep the
  radio out of power save, which is what restores both mDNS replies and a steady stream;
- a **partial wake lock**, because a foreground service is not killed but its threads are
  still frozen in deep doze — and the capture pipeline and HTTP server are plain coroutines.

Both released in `onDestroy`, so an idle phone pays nothing.

### Done: backgrounding the viewer killed the stream and every alert

**Your report:** leave the app and the Live Update says disconnected or reconnecting, and no
alerts arrive.

**Cause:** the camera end has had a foreground service since the beginning. The watching end
had **nothing**. Switching to another app therefore gave Android permission to freeze the
process — the MJPEG stream and the control socket were cut, the shade fell to "Reconnecting",
and no alert could arrive because nothing was listening for one.

**Fix:** a `ViewingService`, the mirror of the broadcast one — a `mediaPlayback` foreground
service (it plays the nursery's audio, so that is the honest type) holding the same wake and
WiFi locks, started and stopped by the live view through a new `PlatformViewingSession`
expect/actual. `START_NOT_STICKY`, unlike the camera: a restarted viewing service would be
watching nothing.

### Done: "who's watching" was wrong

**Cause:** viewers were counted on `/stream`. That connection is reopened on every blink,
reconnect and backgrounding, and each of those incremented the count going in and decremented
it coming out — so one television could read as "2 watching", and a viewer that walked out of
range stayed counted until its socket timed out.

**Fix:** the count is taken on the **control channel** instead. A viewer opens exactly one and
holds it for as long as it is watching, which is the connection whose lifetime actually means
"someone is watching".

### Done: Tailscale devices are discoverable

**Your report:** Tailscale is installed, you connect to the home instance, discovery still
finds nothing.

**Why it could never have worked:** a tailnet is a mesh of point-to-point WireGuard tunnels
and **carries no multicast**. mDNS is entirely multicast. So a camera reachable over Tailscale
is invisible to `_babymonitorpro._tcp` by construction — no amount of fixing the browser
changes that. The same is true of guest networks and any router that filters mDNS.

**So the tailnet is probed instead of browsed.** New `Tailnet` + `CameraProbe`:

- Tailscale allocates every node an address in **100.64.0.0/10**, and a node can see its own.
  That address was invisible to this app until now, because `localIpv4Addresses()` filters to
  RFC1918 — correctly, since that list feeds the pairing card. A new unfiltered
  `allIpv4Addresses()` sits beside it.
- When this device holds a tailnet address, the Find screen probes the **/24 it sits in** plus
  **100.64.0.0/24** (where Tailscale starts allocating), asking each for `/info` on the camera
  port. Anything that answers as a camera is offered, under the name it reports.
- Bounded hard: ~500 addresses, 24 sockets at a time, 1.2s each, one port, one path. A full
  sweep of 100.64.0.0/10 is four million addresses and is never attempted — that is a port
  scan, not a feature. Nothing is probed at all on a device with no tailnet address.
- The card is its own section, **"Over Tailscale"**, with its own Search again.
- **Three ports are asked, not one:** this device's configured port, the new default (47821)
  and `Bmp.LEGACY_PORT` (8080). The two ends of a pair are updated at different times, and a
  viewer that only asks the new port reports "nothing found" for a camera that is working
  perfectly well on the old one — which is exactly what happened on the first attempt here.
- **The /24 of any camera in Recently connected is probed too**, ahead of the guesses.
  Tailscale allocates across the whole /10 fairly arbitrarily, so a peer is often *not* in
  this device's own /24 — that is the honest limit of scanning. An address that has worked
  before is evidence about where the household's nodes actually live, and evidence beats a
  guess, so those blocks are asked first and the cap cannot squeeze them out.

**The limit, stated plainly:** if the camera sits outside the probed blocks, scanning will not
find it. A full sweep of 100.64.0.0/10 is four million addresses and is not something this app
will ever do. The reliable first contact is to type the camera's tailnet address once — the
camera's own pairing card now shows it — after which it is in Recently connected and its
block is probed automatically.

The camera end helps too: the pairing card now offers the camera's tailnet address alongside
its LAN one, because that is the address a device watching from outside the house needs and
there is nowhere else on the phone to find it.

### Done: recently connected devices

Up to six cameras that actually answered, newest first, stored as `host|port|time|name` and
offered as tappable rows on the Find screen. Recorded when a camera sends its first status —
an address that refused is not worth offering tomorrow — and under the name the camera calls
itself, which matters most for a tailnet address like `100.87.4.19`.

This is also the reliable path for anything discovery cannot reach, tailnet or otherwise.

### Done: the streaming phone's battery

`Status` and `/info` now carry `batteryPercent` and `charging`, read fresh on the camera
(`BatteryManager` on Android, `UIDevice` on iOS, unknown on desktop/web). The viewer shows it
beside the camera's name — but only when it is **low (≤20% and not charging)** or charging.
A number that is always on screen is a number nobody reads; this one matters at 4am, and
"8% on a charger" is a different fact from "8% on a shelf".

### Done: low-bandwidth mode

For watching over mobile data. `GET /stream?fps=N` — the camera **drops frames** for that one
viewer. Not re-encoding: recompressing per viewer would pile the work onto the nursery phone,
the oldest device in the system. MJPEG sends a whole picture per frame, so dropping is very
nearly linear — 2 fps is a sixth of the data of 12, and "is she still asleep" is a question
2 fps answers.

Viewer setting **Data saver**: Off / 5 / 2 / 1 fps, persisted, applied per connection. Absent
means full speed, which is right on home WiFi.

### Done: change the nursery device's settings from the watching device

New `SetCameraSettings` control message — every field nullable, null meaning "leave it alone",
so a viewer turning the torch on need not restate six other settings. New
`RemoteCameraControls` panel, opened from a **Camera** button in the live view's bar and rail:

- lens, torch, movement sensitivity, sound sensitivity — applied immediately;
- picture size and frame rate — these rebind the capture pipeline, so the panel says plainly
  that the feed will blink;
- device name, which re-registers mDNS.

Every change is answered with a fresh status to **all** viewers, because two parents watching
one cot must not disagree about which way the lens is pointing. The panel reads from the
camera's reported status rather than local state, so a request the camera refuses simply never
changes the reading.

### Done: zoom the feed

Pinch to zoom to 4x, drag to pan, double-tap to reset. Bounded at 4x (where the JPEG blocks
take over from the baby) and the pan is clamped to the picture's own edges, so it can never be
dragged into a black screen you have to guess your way out of. Entirely local — no extra data,
and nothing asked of the camera. Off on a television, which has nothing to pinch.

### Done: the chrome stops disappearing

**Your ask:** leave the UI up — live, latency, sound — in portrait too, not only landscape.

It auto-hid four seconds after the last touch on a phone. The things it took with it are
exactly the things a parent glances at the phone *for*: whether the feed is live, the latency,
and the Sound control. Now it never hides, on any device or orientation. That also removes the
last focus trap on a television, where there was no tap to bring it back.

### Verified

Built for Android, JVM and wasm; `:shared:jvmTest` passes. Installed on the Tecno (over
wireless adb at `192.168.0.154` — the address had changed) and pushed to its Downloads folder
for the TV. App launches clean, no fatal exceptions, Find screen renders with its new sections.

**Not yet verified, and needing you:**

- The Huawei was not connected to adb this session, so it still has the previous build.
- The Tailscale card cannot be checked from here: the Tecno holds no 100.x address right now,
  so the probe correctly stays hidden. Turn Tailscale on at both ends and the "Over Tailscale"
  card should appear and find the camera.
- Battery, remote camera controls and data saver all need a live camera to exercise.

---

## Session 7 — 2026-09-16 — reorganisation, and a real TV to test on

### Fixed: the Camera panel looked like it did nothing

**Your report:** the Camera button in the feed did nothing.

Two causes, and the first was mine. `RemoteCameraControls` was emitted **before** the
full-screen video `Row`, which has a black background — so the panel was composed, laid out
and focusable, and then painted over. Both now sit in an explicit `Box` with the panel last.

The second cause is the one still outstanding for you: the camera phone is a build behind.
`SetCameraSettings` is a new message type, and the old camera's decoder drops any type it does
not know — deliberately, so a newer viewer cannot break an older camera. Until that phone is
updated the panel will open and move and the camera will ignore every instruction.

### Settings, reorganised

**Your report:** the settings page feels hard.

It was two long sections stacked, so anyone looking for "keep the screen on" scrolled past
nine camera controls belonging to a job their device may never do.

- A **two-button switcher** at the top — *This device as camera* / *This device watching* —
  showing one at a time. The screen is about a page long now.
- It opens on the half that matches this device: the startup role if one is set, and always
  **watching** on a television, which has no camera.
- The switcher is full-width buttons rather than small pills, because it decides what the rest
  of the screen contains — that is a heading you press, not a filter chip.

### New: what the app does when it opens

**Your ask:** toggles to open on Watch or on Camera.

One choice of three rather than two toggles — **Ask me** (default) / **Camera** / **Watching**
— because two switches can both be on and "open as camera AND open watching" has no meaning.
A device can only do one of them first.

The nursery phone is always the camera and the kitchen tablet is always watching, and both
were asking the same question at every launch, at night, of someone holding a baby. A tapped
alert still wins over the preference: that is a more specific instruction.

### Verified on a real Android TV

Set up an Android TV emulator on this machine — 1080p, API 36, x86_64, KVM — and drove it with
D-pad key events. To make room: removed two system images that backed no AVD, then (with your
go-ahead) the WearOS AVD and image and the Pixel AVD. 15 GB free now.

What the emulator confirmed:

- The app **launches from the leanback launcher**, so the manifest work is right.
- `isTelevision` detects correctly through `UiModeManager` — a phone-shaped APK sideloaded
  onto a TV is recognised as a television, which is exactly how it arrives on yours.
- **Focus rings render complete** on tab pills, role cards and choice pills. The clipping you
  reported is gone: drawn after the content, inset 6dp, no scale.
- **D-pad navigation** moves correctly between the tab strip, the cards and the switcher.
- **Overscan gutters** hold everything clear of the panel edges.
- Settings **opens on "This device watching"** on a TV.

One flaw found and fixed there: focus landed on the section switcher, and Compose scrolled it
into view — which pushed the screen's own title and the night button off the top before the
parent had touched anything. The anchor moved to the first control, so arriving scrolls
nothing. A `ChoiceRow` can now mark its first pill as a screen's landing place.

Also from the TV audit: the remote-settings panel's full-screen scrim is no longer a focus
target on a television, where a screen-sized focusable would swallow every D-pad press before
the panel's own controls saw one.

---

## Session 8 — 2026-09-16 — the emulator earns its keep

Set the TV emulator up as a **real second device**: its cameras bound to the laptop's webcam
(`hw.camera.back/front = webcam0`), audio input enabled, and its port bridged onto the LAN
(`adb forward` plus a `socat` listener on the host's 192.168.1.134) so a phone can watch it.
An emulator is behind NAT, so without that bridge nothing outside can reach it.

That setup immediately found four bugs that no amount of reading would have.

### Bug: one-lens devices produced no video at all

The emulator has a single camera. The app asks for the **front** lens by default, CameraX
threw `No available camera can be found`, and the failure was silent in every way that
counts — the server still answered `/stream` with a body that never produced a byte, and the
camera screen sat on "Starting" forever.

Any single-lens device hits this: tablets, TV boxes, a handset with a dead front module.
`bind()` now tries the requested lens, then the other one, then no lens requirement at all,
and mirrors back whichever it actually got so the UI and the viewer's remote controls agree.

### Bug: "this camera has no microphone available" about a working microphone

`isMicrophoneAvailable()` trusted `FEATURE_MICROPHONE`. An Android TV box declares no such
feature — a television has no built-in mic — while recording perfectly well from a USB or
virtual input. Every viewer was told there was nothing to listen to.

The flag is now a hint. When it is absent the app asks the audio system directly: build an
`AudioRecord` at the configured format, see whether it initialises, release it. That is the
only question that matters, and it is asked once per broadcast.

### Bug: the battery reading wrapped one character per line

Visible the moment a phone watched the emulator: the bar showed a teddy, a vertical column
spelling `0%·charging` down the middle, and no camera name. The name had no weight, so it
claimed the row and left the battery a few pixels to render in.

Fixed with a weight on the name and `softWrap = false` on the battery — and then the deeper
problem showed through, below.

### The live view's bar, reorganised

**Your report:** the buttons are clogged up while watching.

The bar carries six things — mark, name, address, battery, sound pill, three actions — and on
a 393pt screen that does not fit on one line. It is now **two rows on a phone** (identity,
then controls) and one row on anything wider.

Written as two explicit rows rather than a wrapping layout: a flow layout counts every spacer
as an item and broke the controls across three lines in an order nobody chose. The camera
button also became a **glyph** rather than the word "Camera", which is what had finally
squeezed the name out of existence.

### New: the viewer can turn the picture

**Your ask:** let the viewer rotate the video.

A rotate control in the bar, a quarter turn per press, applied on the watching device only —
it asks nothing of the nursery phone and costs no bandwidth. The camera is propped against a
cot rail or wedged under a mattress, and whichever way *it* thinks is up is frequently not the
way the room is; the viewer is the end that knows how the parent is holding their phone.

### Bug: remote settings changed nothing on the camera

**Your report:** changed the TV camera's settings from the phone, and the TV camera did not
reflect it.

Two halves. The camera phone being a build behind is one (an old camera drops the unknown
`SetCameraSettings` message by design). The other was mine: remote changes were applied to the
*running broadcaster* and never written to the camera device's own settings — so that device's
settings screen went on showing the old values, and the next start read them back from storage
and undid everything.

`Broadcaster.onRemoteSettings` now hands every remote change to the app layer, which writes it
through to `AppSettings`. The torch is deliberately **not** stored: it is what the room is
doing right now, not a preference, and a camera that came back from a restart with the light
on would be a camera that woke the baby.

### Bug: a tapped alert opened the camera list

Found while testing the new startup preference. Both the deep-link handler and the startup
navigation run on the first composition, and the deep-link handler *consumes* the pending link
as it navigates. The startup effect read the pending value inside itself, saw an empty one,
and pushed Find on top of the live view. It now snapshots "did we arrive by link" before any
effect runs.

### Settings section follows the startup role

**Your ask.** If this device opens as the camera, the settings screen opens on *This device as
camera*; if it opens watching, it opens on *This device watching*. The television default only
decides when nothing has been chosen.

### Verified on the two devices

Phone watching the emulator's webcam over the LAN bridge: picture live at ~280ms, **Sound on**
available (mic probe working), bar laid out in two clean rows with name, battery, sound,
rotate, camera and Close all legible, and the rotate control turning the picture a quarter turn
per press.

---

## Session 9 — 2026-09-16 — the emulator as a device, and a readable threshold

### The AVD, made usable

**Your report:** the emulator is slow and laggy.

Three causes, and the biggest was not the emulator's specs:

- **`hw.gpu.enabled = no` in the AVD config.** Every frame was composited in software even
  though `-gpu host` was on the command line. Now `host`, running on the real Intel Iris Xe.
- **A starved VM:** 1536 MB of RAM and 4 cores for an Android 16 TV image. Now 6 GB, 6 cores,
  768 MB VM heap, 8 GB data partition.
- **The host itself was saturated.** `llama-server` (Ollama, root-owned) was taking 468% CPU
  mid-inference — nearly five of twelve cores — with the Gradle daemon on another third. The
  emulator was getting the leftovers. Stopped the Gradle daemon; Ollama needs
  `sudo systemctl stop ollama` from you, and was idle again by the time we looked.

Also turned all three guest animation scales to 0, which is most of the perceived speed.

Input round trips now measure 0.07s.

### Bug: the keyboard covered the Connect button

**Your report:** the on-screen keyboard overlays the screen and Connect cannot be pressed.

Not an emulator quirk — the Find screen had no `imePadding`, so the IME simply covered the
bottom of the screen, which is exactly where Connect sits, directly under the field being
typed into. It happens on a phone in landscape too; it is merely worst on a television, where
the leanback keyboard is enormous.

Fixed with `imePadding()` on the scrolling column, and the field's own keyboard action is now
**Go**, which connects — so the button is no longer the only way out of the form.

### New: the alert history follows the newest entry

**Your ask:** auto-scroll "Earlier today" to the latest.

The list is newest-first and the card kept its scroll offset as entries were prepended, so a
parent who had scrolled down to read something older stayed parked there while new alerts
piled up above them, unseen. It now animates back to the top whenever one arrives.

### New: the sound threshold is a reading, not a guess

**Your ask:** draw a live line on the sound slider showing where the room's noise currently
reaches, so the percentage can be chosen rather than guessed. And show it everywhere the
threshold is set.

The slider asks for a percentage; the room produces an RMS figure; nothing connected the two.
`SoundDetector.sensitivityFor()` is the inverse of the threshold curve, so a measured level
maps onto the slider's own scale — "this noise would alert at 62% and above".

`SoundLevelMarker` draws that over the track, with two marks:

- a **live line** tracking the current chunk, marigold once it is past the threshold;
- a **peak line** holding the loudest of the last few seconds and decaying back, because the
  noise worth setting a threshold against — a cough, a door, one cry — is over long before
  anyone has looked up at the screen.

It appears in **both** places the threshold is configured: the camera's own settings screen,
and the viewer's remote camera panel, where it is fed by the audio that device is actually
receiving. Where nothing is being measured — sound off, or not broadcasting — it says so
rather than drawing a mark pinned at zero, which would read as a silent nursery.

The prose under the slider now reads "the room right now would alert at 41% and above" rather
than describing the bars.

### Installed

Built for Android, JVM and wasm; `:shared:jvmTest` passes. Installed on the TV emulator and
the phone, and pushed to the phone's Downloads for the television.

---

## Session 10 — 2026-09-16 — the slider now means what it looks like it means

### The threshold scale was inverted

**Your words:** "the room reports 60%, a fan is running, I want to ignore it, so I set 70% and
it alerts only above 70". Same for movement.

That is the obvious reading, and it was the opposite of what the app did. The detectors think
in **sensitivity** — how little it takes to set them off — so a *higher* number meant a
*lower* bar, and setting 70 against a fan reading 60 would have alerted on the fan
continuously. The number went down as the room got louder.

Both sliders are now **levels**, on the same scale as the live mark:

- bigger number = the room has to be louder, or moving more, before anything fires;
- the reading and the control are in the same units, so "the room is at 60, set the line to
  70" does exactly what it says;
- the inversion happens once, at the UI boundary (`sensitivityForThreshold` /
  `thresholdForSensitivity`), so the wire protocol, the stored settings and the detectors are
  all untouched.

Labels changed from "Alert me at" to **"Alert above"**, and the helper text now reads: *"The
room is at 43% right now. Alerts fire when it reaches 70% — so with a fan running at 43%, set
the line above it."*

Applied in **both** places a threshold is set: the camera's settings screen and the viewer's
remote panel.

### The live mark, for movement too

The motion detector already computed a changed-pixel ratio per frame and threw it away.
`Broadcaster.motionLevel` now publishes it exactly as `soundLevel` does, so the movement
slider gets the same live mark and peak-hold as the sound one — on the camera's settings
screen, where the picture is actually being analysed.

The viewer's panel gets the explanation but no movement mark: the camera sends motion
*events*, not a continuous reading, and inventing one on the viewer would be a lie.

### Both alert kinds now explain themselves

**Your ask:** explain movement alerts on both pages.

Above each slider, in both the settings screen and the viewer's panel:

- **Movement:** "The camera compares each frame with the one before it and alerts you when
  enough of the picture has changed. It does not know what a baby is: a kicked-off blanket
  counts, and so does someone walking in."
- **Sound:** "The camera measures how loud the room is... It does not tell crying from a lorry
  going past — loud is loud."

Both say what the detector *cannot* do, which is the part that decides whether a parent trusts
an alert at 3am.

### The alert history reads the right way round

**Your report:** it does not auto-scroll to the end.

It was newest-first, so "the end" was the top and there was nothing to scroll to. Defensible
on paper, wrong in the hand: an event list is read like a conversation, and the last one means
the one at the bottom. Now oldest-first, newest at the end, and the card follows it — keyed on
the scroll extent as well as the count, because the new row has not been laid out at the
moment the count changes.

Chronological order also makes a run of alerts legible as a run: three in five minutes reads
as a baby waking up.

### Installed

Android, JVM, wasm and `:shared:jvmTest` all pass. On the TV emulator, on the phone, and
pushed to the phone's Downloads for the television.

---

## Session 11 — 2026-09-16 — parity between phone and television

**Your point:** the same features should exist on a phone and on a TV, or they are not
features. Three things were phone-only, and one message was lying.

### Rotate, on the television

The rotate control was added to the phone's bottom bar and nowhere else, so the TV — which
uses the rail instead — never got it. The rail now has it, and the television is the device
that needs it most: the nursery phone is propped at whatever angle the shelf allows, and
nobody is going to walk in and straighten it at 2am.

### Zoom, on the television

Zoom was pinch-and-double-tap, which is no feature at all on a remote. The rail now has
**zoom out / zoom in** buttons with the current factor printed between them, half a step per
press over the same 1x–4x range. The zoom state moved up to the screen so both routes drive
one value, and the pan re-clamps whenever the zoom changes by either route — a button can zoom
out from under a pan in a way a pinch cannot.

A control at the end of its range dims rather than disappearing: a button that vanishes takes
the remote's focus with it, which is the bug class this app has already been bitten by twice.

### The threshold marks now work on every viewer

**Your report:** the camera panel shows the live threshold on the TV but not on the phone.

The panel was deriving the sound level from the audio *this device* was playing — so it
appeared only when the sound happened to be on, and there was no movement reading at all.

The camera now reports both readings in its `Status`, and sends one **every two seconds**
while broadcasting. Status used to be sent only when something changed, which is right for
everything in it except a live measurement. Two seconds is invisible on the wire — one small
JSON object per viewer — and quick enough that a mark moves while a parent is watching the
room it describes.

So both marks now appear on any viewer, phone or television, whether or not it is playing
sound. The old path stays as a fallback for a camera too old to report levels.

### Alert wording was stuck on two phrases

**Your report:** it always says "faint" for sound and "a lot" for movement.

Correct, and the cause is arithmetic. The bands sat on the **raw** magnitude, and the raw
magnitude is not where the events are:

- a sound alert fires around 0.03–0.1 RMS, well under the 0.25 the "loud" band wanted, so
  **every** sound alert read "Faint";
- a motion alert cannot fire below its own threshold, which at the default is already past the
  "a lot" band, so **every** movement alert read "A lot of movement".

Two labels doing no work whatsoever. They are now banded on the same normalised 0..100 scale
the sliders and the live marks use — quarters of it — so the words move with the room, and
they are the same numbers the threshold was set against. Sound reads Faint / Quiet but there /
Clearly audible / Loud; movement reads Slight / Some / Plenty / A lot.

The conversion moved to the detectors (`levelPercent`), which is where it belongs — the UI
helpers now delegate rather than keeping their own copy of the arithmetic.

### Installed

Android, JVM, wasm and `:shared:jvmTest` pass. On the emulator, the phone, and the phone's
Downloads for the television.

---

## Session 12 — 2026-09-16 — a frozen picture is the worst failure there is

### The camera died when the phone locked, and never came back

**Your report:** after locking the Tecno the stream froze on the AVD — and then froze in the
Tecno's own preview too.

**Measured:** both a locked and an awake sample returned *exactly* 53,094 bytes. Identical
byte counts are not a slow camera, they are no camera: that was the replay buffer being
handed to each new connection, with nothing behind it. Logcat showed the capture session
being disconnected around the lock.

**Why it is the worst possible failure:** nothing throws. The foreground service stays up, the
server goes on answering `/stream`, and every viewer keeps showing the last frame it received.
A frozen picture of a sleeping baby looks exactly like a sleeping baby.

**Fix — the capture pipeline is now restartable, and watched.** `startVideo()` was split out
of `start()`, and a watchdog checks every two seconds whether a frame has arrived in the last
six. Six seconds is roughly fifty missed frames at the slowest rate the app offers, so a
merely slow camera is never restarted — only a dead one. Recovery tears the controller down
and builds a new one, because a disconnected CameraX session cannot be revived: rebinding the
same instance just hands back the same closed device.

**Verified on the Tecno, locked:**

| | bytes per 8s |
|---|---|
| Awake, baseline | 6.6 MB |
| Locked, 30s in | 6.8 MB |
| Locked, 45s in | 7.05 MB |

Full rate with the screen off, sustained. The AVD's picture was confirmed moving rather than
still by comparing two screenshots six seconds apart.

The watching end was tested the same way earlier: locking the AVD left the phone still
reporting "1 watching", with `ViewingService` alive — the foreground service work from session
6 holding up.

### The rail, rebuilt around what fits

A run of small reports, all from the television:

- **The controls fell off the bottom.** The rail is a column and its middle grows; once
  "Earlier today" filled up, Sound, rotate, the camera panel and Close were pushed off the
  screen, leaving a parent looking at a list of alerts with no way to turn the sound on or
  leave. The history card now takes the *remaining* height and its list scrolls internally,
  so the controls cannot move and every card stays whole.
- **A clipped card edge.** With an outer scroll, the card's rounded bottom sat under the
  viewport edge and read as broken rather than scrolled. Gone with the same change.
- **Zoom, and then pan.** Pinch is not available on a remote, so zoom became buttons — and
  panning needs its own, because the D-pad is busy walking focus. One row: zoom out, the four
  arrows, zoom in. The arrows are always present, dimmed at 1x rather than hidden, because a
  control that appears and disappears under the remote is how focus gets lost.
- **The zoom factor label was dropped** — a parent can see how far in they are by looking at
  the picture, which is what they are already doing, and the row needed the width.
- **One action row:** rotate, camera settings, Sound (taking the spare width, because its
  label is the only one that has to say which way it is set), and Close as a **✕**. "Sound on"
  had been clipping to "Sound", which is a switch whose label does not say its state.
- **The meter card is shorter on a television** (24dp of bars, tighter padding), since the
  rail there also has to hold zoom, pan, sound and three actions.

### Installed

On the emulator, the phone, and the phone's Downloads for the television.

---

## Session 13 — 2026-09-16 — permission to stay alive

**Your question:** does the app need permission to run in the background, and are all the
permissions it needs already requested while streaming?

Checked rather than assumed, on the device.

**Already requested at runtime**, on the camera screen: `CAMERA`, `RECORD_AUDIO`,
`POST_NOTIFICATIONS`. **Declared and used**: the three foreground-service types
(camera, microphone, mediaPlayback), `WAKE_LOCK`, and the WiFi and multicast permissions.

**Missing — and the Tecno proved it:**

```
dumpsys deviceidle whitelist → not in the list
am get-standby-bucket        → 5   (RESTRICTED)
```

A foreground service plus a wake lock is the supported way to stay alive, and on stock Android
it is enough. Outside the battery-optimisation exemption the app is still subject to Doze and
to app-standby, and bucket 5 is Android already treating it as restricted — which is very
likely part of why the camera died on lock.

### Added: a background-access row, at the top of Settings

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is now declared, and asked for from the settings screen
rather than silently. The row sits above the role switcher, because it is neither a camera
setting nor a viewer one, and it has three states:

- **Granted** — plain card, "Allowed to run in the background".
- **Restricted** — an error-coloured card, "Android may stop this app… streaming or watching
  can stop minutes after the screen locks", with an **Allow** button opening the system dialog.
  It re-reads every 1.5s, so it updates when the parent comes back from that dialog.
- **No such setting** — plain card, "This device has no battery optimisation to turn off".

### The television was showing a warning it could not act on

**Your report:** the TV still shows the banner and the Allow button.

**Cause:** Android TV *does* resolve `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — to
`com.android.tv.settings.EmptyStubActivity`, a deliberate no-op. A non-null resolution is
therefore not evidence that anything will happen, and my check believed it.

**Fix:** the question is skipped outright on a television — it is mains powered and has no
battery optimisation — and any resolution landing on a stub activity is rejected as well, for
TV-like boxes that do not report themselves as televisions. Verified on the emulator: the card
now reads "Runs in the background · This device has no battery optimisation to turn off", with
no button.

### What no app can request

The card also names the part Android does not govern: **Tecno/Infinix (HiOS), Huawei (EMUI),
Xiaomi (MIUI)** and others keep their own app-killer lists — "Protected apps", "App launch",
"Autostart" — and enforce them regardless of foreground services. There is no API for these by
design, so the row says so and offers an **Open app settings** button that lands one tap away,
rather than guessing at an OEM activity that may not exist.

Where to find them:

| Phone | Setting |
|---|---|
| Tecno / Infinix (HiOS) | Battery → Background app management, or Phone Master → App auto-launch |
| Huawei (EMUI) | Battery → **App launch** → Manage manually → all three switches on |
| Xiaomi (MIUI) | Apps → the app → Autostart, plus Battery saver → No restrictions |
| Samsung | Battery → Background usage limits → remove from Sleeping apps |

**Status:** granted on the Tecno. The Huawei still needs both the in-app Allow *and* EMUI's
App launch, and it is the device used as the camera.

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
