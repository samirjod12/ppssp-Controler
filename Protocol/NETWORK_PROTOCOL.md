# Network Protocol v1

Transport: WebSocket (`ws://<pc-ip>:<port>/`) over local TCP. Text frames, UTF-8 JSON. Maximum frame size: **1024 bytes**. Larger frames are rejected and the connection is closed.

Every message has `"type"` and `"version"` (integer, currently `1`). Unknown types, unknown fields that change meaning, wrong versions, or malformed JSON are rejected.

## Session flow

1. Client connects.
2. Client sends `hello` within 5 seconds. Server replies `welcome` or `error` (then closes).
3. Only after `welcome` are `button`, `analog`, `heartbeat` accepted. Anything earlier closes the connection.
4. Client sends `heartbeat` every 1 s. Server releases all inputs if nothing is received for 3 s and closes.
5. On close/timeout/stop/mode switch, the server releases every active input.

## Messages

### hello (client -> server)
```json
{ "type": "hello", "version": 1, "pin": "483920", "clientName": "Pixel 8" }
```
- `pin`: exactly 6 digits. `clientName`: 1-40 printable characters.
- Server compares PIN in constant time. After 5 failed attempts the client IP is blocked for 60 s.
- The server keeps only a salted hash of the PIN at rest; the PIN is regenerated each server start.

### welcome / error (server -> client)
```json
{ "type": "welcome", "version": 1, "serverName": "DESKTOP-ABC" }
{ "type": "error", "version": 1, "code": "bad_pin", "message": "Pairing failed" }
```
Codes: `bad_pin`, `bad_message`, `unsupported_version`, `rate_limited`, `not_paired`, `busy` (another controller is already connected; one controller at a time).

### button (client -> server)
```json
{ "type": "button", "version": 1, "button": "CROSS", "state": "down" }
```
- `button`: `UP DOWN LEFT RIGHT CROSS CIRCLE SQUARE TRIANGLE L R START SELECT`
- `state`: `down` or `up`. Duplicate events (down while down) are ignored by the server.

### analog (client -> server)
```json
{ "type": "analog", "version": 1, "x": 0.65, "y": -0.35 }
```
- `x`, `y` in [-1.0, 1.0]; out-of-range values are clamped, NaN/Infinity rejected. +x right, +y up.
- Client rate limit: at most 60 messages/s, and only when the value changes.

### heartbeat (both directions)
The server answers each client heartbeat with a heartbeat, so the client can detect a dead server too.
```json
{ "type": "heartbeat", "version": 1 }
```

## Security rules
- The server listens on all interfaces (it uses a raw TCP listener so no administrator rights are needed) but immediately drops any client whose address is not loopback, RFC1918 (10/8, 172.16/12, 192.168/16), link-local, or IPv6 unique-local. Do not port-forward it.
- No received value is ever executed, used as a path, or used as a key code. Buttons map through a fixed enum.
- Version 1 is unencrypted `ws://` on the LAN; the PIN prevents unauthorized control but traffic is not confidential. Use trusted networks only.
