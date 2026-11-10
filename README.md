# PSP Wireless Controller

Turn an Android phone into a PSP-style controller for a Windows PC over local Wi-Fi.

- **PSP Controller** (Android, Kotlin + Jetpack Compose) sends input.
- **PSP Receiver** (Windows, C# .NET 8 WPF) receives it and outputs keyboard keys (SendInput) or a virtual gamepad.
- Everything is built by GitHub Actions. No local Android Studio / Visual Studio needed.

## Status (honest tracker)

Legend: Implemented / Compiled / Unit tested / Hardware tested. Nothing is marked beyond what has actually been done.

| Area | Implemented | Compiled | Unit tested | Hardware tested |
|---|---|---|---|---|
| Protocol spec | yes | n/a | n/a | n/a |
| Message validator, input tracker, key defaults | yes | not yet (CI) | written, not yet run | no |
| PIN manager, rate limiter, LAN-only filter, WS handshake, settings | yes (Phase 2) | not yet (CI) | written, not yet run | no |
| WebSocket server (pairing, heartbeat timeout, release on every exit) | yes (Phase 2) | not yet (CI) | loopback integration tests written, not yet run | no |
| WPF dashboard (start/stop, IP, port, PIN, device, counter, mode, status) | yes (Phase 2) | not yet (CI) | no (UI) | no |
| SendInput keyboard output (scan codes, shared-key ref-counting, release on every exit) | yes (Phase 3) | not yet (CI) | fake-injector tests written, not yet run | no |
| Key mapping editor + conflict warning, Test Controller window, tray, start with Windows | yes (Phase 3) | not yet (CI) | settings/bindings tests written; UI and registry not unit tested | no |
| Android app: PSP-style landscape UI, multi-touch D-pad / face buttons / shoulders / Start / Select, analog stick with dead zone + sensitivity, connect + PIN screen, saved PCs, settings, diagnostics | yes (Phase 4) | not yet (CI) | no (UI) | no |
| Android networking: WebSocket client, hello/PIN, 1 s heartbeat, dead-link detection, auto-reconnect, release-all on background/disconnect, duplicate-safe input sender | yes (Phase 5) | not yet (CI) | JVM tests written (protocol, analog math, sender, geometry, settings, connection state machine), not yet run | no |
| Android CI workflow | yes | not yet run | n/a | n/a |
| Virtual gamepad backend | no (Phase 7) | no | no | no |
| Release workflows (android-release, windows-release) | no (Phase 10-11) | no | no | no |

## Layout

See `Protocol/NETWORK_PROTOCOL.md`, `WindowsReceiver/`, `AndroidController/`, `.github/workflows/`.
