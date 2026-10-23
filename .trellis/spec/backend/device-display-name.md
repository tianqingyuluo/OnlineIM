# Device display name

Readable label for each logged-in session. The device id stays the session key; the list must not show it as the name.

## Scenario: login device list name

### 1. Scope / Trigger

- Trigger: `POST /api/v1/auth/login` registers a session, `GET /api/v1/devices` renders it, and the Vue device panel displays it.
- Redis value `user:sessions:{userId}:{deviceId}` gains `deviceName`. WebSocket connect, close, heartbeat, and token refresh must keep that field.

### 2. Signatures

- `DeviceNames.resolve(clientName, userAgent, deviceType, deviceId) -> String`
- `DeviceSessionService.onLogin(userId, deviceId, deviceType, token, deviceName)`
- Login body field `device_name` (optional). Header `User-Agent` (optional).
- `GET /api/v1/devices` item field `device_name`.

### 3. Contracts

- Request `device_name`: string, optional, max 128 characters (validation). Stored length max 64 after trimming control characters and collapsing whitespace.
- Resolution order: sanitized client name, unless it equals `device_id`; else User-Agent browser/OS; else `桌面端` / `手机端` / `网页端` from `device_type`.
- User-Agent shape: `{类型} · {浏览器} / {系统}`. Either half may be missing. Only the first 512 characters of the header are inspected.
- Vue sends `device_name` from `localStorage onlineim.device_name`, or from `ua-parser-js` browser and OS with the same shape (`Mac OS` is sent as `macOS`).
- Response `device_name` is never blank and never the raw `device_id`. Old Redis rows without `deviceName` still get the type label.
- `device_name` is not a JWT claim and does not affect kick / presence.

### 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| `device_name` longer than 128 | HTTP 400, login does not run |
| blank / whitespace `device_name` | ignored; User-Agent or type label is used |
| `device_name` equal to `device_id` | ignored |
| missing User-Agent and missing name | type label |
| unrecognized User-Agent | type label |
| WebSocket reconnect or refresh-token | stored name kept |

### 5. Good/Base/Bad Cases

- Good: desktop Chrome on Windows → `桌面端 · Chrome / Windows`. Phone Safari → `手机端 · Safari / iOS`. Client name `我的手机` is stored as-is.
- Base: no name and no User-Agent → `桌面端` or `手机端` or `网页端`, which is enough to tell the slots apart.
- Bad: showing `web-abc123` (or any `device_id`) in the device list.

### 6. Tests Required

- `DeviceNamesTest`: desktop vs phone User-Agent, Edge not labeled Chrome, iPhone not labeled macOS, client name wins, id-as-name falls through, truncation.
- `DeviceSessionServiceTest`: stored name survives WebSocket connect; missing name and id-as-name become the type label; a map-shaped Redis value still returns `deviceName`.
- `device-session.test.ts` and `device-session-panel.test.ts`: login payload and the panel render the readable name, and do not render the device id.

### 7. Wrong vs Correct

#### Wrong

Rebuild `RedisConnectionMeta` on connect or heartbeat without copying `deviceName`, or render `device_id` when `device_name` is absent.

#### Correct

`onLogin` writes `deviceName`. Later updates load the existing meta (or the Jackson map) and write it back. `list` fills a type label when the stored name is blank or equal to the device id. The panel uses `deviceDisplayName`.
