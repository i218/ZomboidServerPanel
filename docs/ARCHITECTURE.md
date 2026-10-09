# Integration boundaries

## Current release

ZombieBuddy registers the Java Lua bridge. Server-only Lua calls onTick; Java verifies GameServer.server before reading configuration or starting any worker. PzRuntime isolates game reflection. Only the game thread touches these APIs.

PanelReporter sends JSON to POST /api/hosts/{id}/heartbeat through Java HttpClient. It uses a single daemon scheduler, bounded timeouts, no redirects and no HTTP proxy. It never reads commands. Tick freshness uses monotonic time; losing ticks changes health without pretending the process has exited.

CPU is sampled from ProcessHandle and normalized by available logical processors. Heap usage and uptime come from JVM management APIs. Each server name gets its own config/log directory and stable host ID. Deployments with identical names on the same panel must choose distinct hostId values.

Configuration is loaded once; invalid configuration disables monitoring until restart. Missing configuration is created with defaults. Shutdown closes the worker; there is no game shutdown adapter, process kill, external shell, or restart marker in this monitoring release.

## Next stage: safe control

This is a design boundary, not implemented behavior:

- Extend the panel contract for game-specific metrics instead of substituting players for request counts.
- Keep OS process start/recovery in ServerPanel; a stopped game mod cannot start its own JVM.
- Execute allowlisted game actions on the game thread with an explicit opt-in configuration.
- Handle panel command leases and durable idempotency before exposing command execution.
- Acknowledge only an observed outcome. Save and announce graceful shutdown before requesting it.
- Verify process identity in the panel before any process-level stop.

The existing panel start/stop/restart queue does not imply permission for this mod to execute those actions.

## Verified adapters and remaining checks

Locally inspected PZ Build 42 and ZombieBuddy 2.3.4 APIs are isolated in Main and PzRuntime. Test doubles verify the server guard and disabled mode; they are not proof of game integration. The compiled Java archive contains no test fixtures or third-party runtime dependencies.

TODO: live dedicated-server launch with ZombieBuddy approval; verify custom cachedir deployment and host appearance in the native panel UI. No game or active user panel is started or stopped by the automated tests.

## Optional client installation (0.1.1)

On the first dedicated-server tick OptionalClientAdapter verifies that the advertised GameServer.ServerMods list differs from ZomboidFileSystem.getModIDs(), then excludes only ServerPanelBridge from the advertised list. The loaded runtime list, server INI, Workshop downloads and other mods are untouched. This also runs with monitoring disabled. No checksum bypass is introduced; the mod contains no shared/client Lua or gameplay resources. A live client join remains a deployment check.

The standalone .NET Framework Windows installer embeds the full mod ZIP and compiled JAR. It writes individual files atomically with backups and rollback, preserving the existing mod directory and runtime config. The optional client checkbox enables discovery through the game; it does not make installation mandatory for connecting players.

0.1.2 adds a separate administrator-only ServerPanelBridgeMenu component inside the unified installer. MLOS rejects local mods with no Workshop ID; this component wraps only the installed ServerPanelBridge case and preserves the original method for other entries. Menu Lua is outside the core server mod to keep it out of server Lua checksums; it is enabled only in the optional client profile, never the server INI.
