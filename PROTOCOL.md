# BabyMonitor Pro protocol

Version **1** (`protocolVersion` in every handshake). LAN only — the broadcaster binds
`0.0.0.0` so other devices on the same WiFi can reach it, and nothing in the app ever
contacts a server outside that network.

- Transport: plain HTTP/1.1 and WebSocket on **port 47821** by default. Not 8080: that port is
  contested on a normal home network, and a camera that cannot bind offers no address at all.
- Discovery: mDNS service type `_babymonitorpro._tcp`.
- Pairing link: `bmpro://<host>:<port>`.

Everything below is served by the device running as **Camera**. Viewers are pure clients.

---

## Authentication

None. Every endpoint is open to any device that can reach the port, and traffic on the LAN
is unencrypted — v1 makes no claim beyond "it never leaves your network".

---

## `GET /info`

Cheap identity probe. Used by the viewer before connecting and by anything that wants to
confirm a host is a BabyMonitor Pro camera.

```json
{
  "deviceName": "Nursery",
  "role": "camera",
  "protocolVersion": 1,
  "streaming": true,
  "videoWidth": 1280,
  "videoHeight": 720,
  "audioSampleRate": 16000
}
```

Every field is always present, including ones sitting at their default — a viewer has no
other way to learn the geometry or the protocol version.

---

## `GET /stream` — MJPEG video

```
Content-Type: multipart/x-mixed-replace; boundary=frame
```

Each frame:

```
--frame\r\n
Content-Type: image/jpeg\r\n
Content-Length: <n>\r\n
\r\n
<n bytes of JPEG>\r\n
```

- ~10–15 fps, ~720p, JPEG quality ~70.
- `Content-Length` is authoritative. A decoder must not scan for the next boundary while
  bytes remain, because JPEG payloads can contain the boundary text.
- The connection stays open until the client drops it. Frames are dropped, never queued,
  for a slow viewer — the newest frame always wins.
- Native viewers decode frames and draw them with Compose. Web viewers hand the URL to an
  `<img>` element, which decodes `multipart/x-mixed-replace` natively.

> **UPGRADE PATH:** this is where WebRTC would replace MJPEG for lower latency and
> bandwidth. The seams are marked in `BroadcastServer` and `ViewerClient`.

---

## `WS /audio` — PCM audio

Binary WebSocket frames, each one chunk of raw PCM:

| Property | Value |
|---|---|
| Encoding | 16-bit signed, little-endian |
| Channels | 1 (mono) |
| Sample rate | 16 000 Hz |
| Chunk | ~100 ms = 1600 samples = 3200 bytes |

No container, no header, no timestamps: write the bytes to the platform's audio output as
they arrive. There is no jitter buffer by design — a monitor should be current, not smooth.

The socket carries audio from camera to viewer only. Two-way talk is an explicit v1
non-goal.

---

## `WS /control` — JSON messages

Text frames, one JSON object per frame, in both directions. Messages are a closed set
discriminated by a `type` field:

```json
{ "type": "MotionEvent", "atMillis": 1770000000000, "intensity": 0.42 }
```

Unknown fields are ignored and unknown `type` values are dropped, so a newer camera and an
older viewer keep working together.

### Viewer → camera

| Type | Fields | Meaning |
|---|---|---|
| `Hello` | `viewerName`, `protocolVersion` | Sent on connect. The camera replies with `Status`. |
| `SetSensitivity` | `motion` 0–100, `sound` 0–100 | Changes detection thresholds. The camera broadcasts a fresh `Status` to **all** viewers. |
| `Ping` | `nonce` | Latency probe. `nonce` is the sender's clock in millis. |

### Camera → viewer

| Type | Fields | Meaning |
|---|---|---|
| `Status` | `deviceName`, `protocolVersion`, `viewerCount`, `streaming`, `audioAvailable`, `motionSensitivity`, `soundSensitivity`, `uptimeMillis` | Sent on connect, on `Hello`, and whenever sensitivity changes. |
| `MotionEvent` | `atMillis`, `intensity` (0–1 changed-pixel ratio) | Motion crossed the threshold. |
| `SoundEvent` | `atMillis`, `level` (0–1 normalised RMS) | Sound crossed the threshold. |
| `Pong` | `nonce` | Echo of a `Ping`; the viewer derives round-trip latency from it. |

Both event types are debounced for 3 seconds on the camera, so a single burst of activity
produces one alert rather than a stream of them.

---

## Detection

Runs entirely on the camera; viewers only receive the events.

**Motion.** Each JPEG frame is decoded and downscaled to a 64×48 grayscale thumbnail. Pixels
whose brightness differs from the previous frame by more than 24 (of 255) count as changed.
Motion fires when the changed fraction crosses a sensitivity-derived threshold, linear from
`0.20` at sensitivity 0 down to `0.005` at sensitivity 100.

**Sound.** RMS of each PCM chunk, normalised against full scale. Fires above a threshold
linear from `0.35` at sensitivity 0 down to `0.01` at sensitivity 100.

Neither classifies anything: no cry detection, no ML. Motion reacts to a kicked-off blanket
as readily as to a baby sitting up, which is the correct bias for a monitor.

---

## Discovery

The camera advertises `_babymonitorpro._tcp` in the `local.` domain, with TXT records:

| Key | Value |
|---|---|
| `name` | Human-readable device name |
| `v` | Protocol version |

Implementations: `NsdManager` (Android), `NSNetService` (iOS), JmDNS (desktop). Browsers
have no mDNS API, so the web viewer always uses one of the two fallbacks below — as does
any device where discovery is blocked by network policy.

**Fallback 1 — manual entry.** `host` or `host:port`.

**Fallback 2 — pairing code.** The camera screen shows both its `host:port` in plain text
and a QR code encoding `bmpro://<host>:<port>`. Viewers with a camera scan it;
everyone else reads the address off the screen.
